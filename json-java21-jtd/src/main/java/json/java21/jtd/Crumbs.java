package json.java21.jtd;

/// Lightweight breadcrumb trail for human-readable error paths
record Crumbs(String value) {
  static Crumbs root() {
    return new Crumbs("#");
  }

  Crumbs withObjectField(String name) {
    return new Crumbs(value + "→field:" + name);
  }

  Crumbs withArrayIndex(int idx) {
    return new Crumbs(value + "→item:" + idx);
  }
}
