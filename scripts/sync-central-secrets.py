#!/usr/bin/env python3
import argparse, os, sys, subprocess, xml.etree.ElementTree as ET, hashlib, base64, shlex

def die(msg):
    sys.stderr.write(msg+"\n"); sys.exit(1)

def which(cmd):
    from shutil import which as w
    return w(cmd)

def run(cmd, input_bytes=None):
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE if input_bytes else None, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out, err = p.communicate(input=input_bytes)
    if p.returncode != 0:
        raise RuntimeError("command failed: %s\n%s" % (" ".join(cmd), err.decode('utf-8','replace')))
    return out

def sha256_str(s: str) -> str:
    return hashlib.sha256(s.encode('utf-8')).hexdigest()

def main():
    ap = argparse.ArgumentParser(description='Sync Central/GPG secrets from local ~/.m2/settings.xml and gpg to GitHub Actions secrets (hashed preview + optional set).')
    ap.add_argument('--settings', default=os.path.expanduser('~/.m2/settings.xml'))
    ap.add_argument('--repo', default=None, help='owner/repo for gh secret operations (defaults to current)')
    ap.add_argument('--key-id', default=None, help='GPG key id/fingerprint to export; auto-detect if omitted')
    ap.add_argument('--set', action='store_true', help='Write secrets to GitHub (CENTRAL_USERNAME, CENTRAL_PASSWORD, GPG_PASSPHRASE, GPG_PRIVATE_KEY, GPG_KEYNAME)')
    ap.add_argument('--delete-first', action='store_true', help='Delete existing secrets before setting')
    args = ap.parse_args()

    if not os.path.exists(args.settings):
        die('Settings not found: %s' % args.settings)

    # Parse settings.xml
    tree = ET.parse(args.settings)
    root = tree.getroot()

    ns = {}
    # servers/server[id=central]
    central_user = central_pass = None
    for srv in root.findall('./servers/server', ns):
        sid = (srv.findtext('id') or '').strip()
        if sid == 'central':
            central_user = (srv.findtext('username') or '').strip()
            central_pass = (srv.findtext('password') or '').strip()
            break
    if not central_user or not central_pass:
        die('Could not find <server id="central"> with username/password in %s' % args.settings)

    # gpg.passphrase (unique)
    passphrases = []
    for prop in root.findall('.//profiles/profile/properties', ns):
        val = prop.findtext('gpg.passphrase')
        if val and val.strip():
            passphrases.append(val.strip())
    uniq = sorted(set(passphrases))
    if len(uniq) != 1:
        die('Expected exactly one gpg.passphrase; found %d: %s' % (len(uniq), ','.join(uniq)))
    gpg_pass = uniq[0]

    if not which('gpg'):
        die('gpg not found on PATH')

    key_id = args.key_id
    if not key_id:
        # auto-detect signing-capable key (sec with s capability)
        out = run(['gpg','--list-secret-keys','--with-colons']).decode('utf-8','replace').splitlines()
        for line in out:
            parts = line.split(':')
            if len(parts) > 12 and parts[0] == 'sec' and 's' in parts[11]:
                key_id = parts[4]
                break
        if not key_id:
            die('No signing-capable secret key found (looked for sec with s capability)')

    # fingerprint from key
    fpr = None
    out = run(['gpg','--with-colons','--list-secret-keys', key_id]).decode('utf-8','replace').splitlines()
    for line in out:
        parts = line.split(':')
        if parts and parts[0] == 'fpr' and len(parts) > 9:
            fpr = parts[9]
            break
    if not fpr:
        die('Could not extract fingerprint for key %s' % key_id)

    # export armored private key using loopback/passphrase; if agent rejects, fallback without pass
    try:
        armored = run(['gpg','--batch','--yes','--pinentry-mode','loopback','--passphrase', gpg_pass, '--armor','--export-secret-keys', key_id]).decode('utf-8','replace')
    except Exception:
        armored = run(['gpg','--armor','--export-secret-keys', key_id]).decode('utf-8','replace')

    # Hashes for comparison
    creds_hash = sha256_str(central_user + ':' + central_pass)
    pass_hash = sha256_str(gpg_pass)
    fpr_hash = sha256_str(fpr)
    priv_hash = hashlib.sha256(armored.encode('utf-8')).hexdigest()

    print('Central user: %s' % central_user)
    print('Key fingerprint: %s' % fpr)
    print('SHA256 central(user:pass): %s' % creds_hash)
    print('SHA256 gpg.passphrase: %s' % pass_hash)
    print('SHA256 gpg.keyname(fingerprint): %s' % fpr_hash)
    print('SHA256 armored private key: %s' % priv_hash)

    if args.set:
        if not which('gh'):
            die('gh not found on PATH (needed for --set)')
        repo_flag = ['--repo', args.repo] if args.repo else []
        if args.delete_first:
            for name in ['CENTRAL_USERNAME','CENTRAL_PASSWORD','GPG_PASSPHRASE','GPG_PRIVATE_KEY','GPG_KEYNAME']:
                subprocess.run(['gh','secret','delete',name,'--app','actions'] + repo_flag, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        # set secrets
        for name, value in [
            ('CENTRAL_USERNAME', central_user),
            ('CENTRAL_PASSWORD', central_pass),
            ('GPG_PASSPHRASE', gpg_pass),
            ('GPG_PRIVATE_KEY', armored),
            ('GPG_KEYNAME', fpr),
        ]:
            proc = subprocess.Popen(['gh','secret','set',name,'--app','actions'] + repo_flag, stdin=subprocess.PIPE)
            proc.communicate(input=value.encode('utf-8'))
            if proc.returncode != 0:
                die('failed to set secret %s' % name)
        print('Secrets updated in GitHub (Actions app).')

if __name__ == '__main__':
    main()

