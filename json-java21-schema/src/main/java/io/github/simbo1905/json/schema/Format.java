package io.github.simbo1905.json.schema;

/// Built-in format validators
public enum Format implements FormatValidator {
  UUID {
    @Override
    public boolean test(String s) {
      try {
        java.util.UUID.fromString(s);
        return true;
      } catch (IllegalArgumentException e) {
        return false;
      }
    }
  },

  EMAIL {
    @Override
    public boolean test(String s) {
      // Pragmatic RFC-5322-lite regex: reject whitespace, require TLD, no consecutive dots
      return s.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") && !s.contains("..");
    }
  },

  IPV4 {
    @Override
    public boolean test(String s) {
      String[] parts = s.split("\\.");
      if (parts.length != 4) return false;

      for (String part : parts) {
        try {
          int num = Integer.parseInt(part);
          if (num < 0 || num > 255) return false;
          // Check for leading zeros (except for 0 itself)
          if (part.length() > 1 && part.startsWith("0")) return false;
        } catch (NumberFormatException e) {
          return false;
        }
      }
      return true;
    }
  },

  IPV6 {
    @Override
    public boolean test(String s) {
      try {
        // Use InetAddress to validate, but also check it contains ':' to distinguish from IPv4
        //noinspection ResultOfMethodCallIgnored
        java.net.InetAddress.getByName(s);
        return s.contains(":");
      } catch (Exception e) {
        return false;
      }
    }
  },

  URI {
    @Override
    public boolean test(String s) {
      try {
        java.net.URI uri = new java.net.URI(s);
        return uri.isAbsolute() && uri.getScheme() != null;
      } catch (Exception e) {
        return false;
      }
    }
  },

  URI_REFERENCE {
    @Override
    public boolean test(String s) {
      try {
        new java.net.URI(s);
        return true;
      } catch (Exception e) {
        return false;
      }
    }
  },

  HOSTNAME {
    @Override
    public boolean test(String s) {
      // Basic hostname validation: labels a-zA-Z0-9-, no leading/trailing -, label 1-63, total ≤255
      if (s.isEmpty() || s.length() > 255) return false;
      if (!s.contains(".")) return false; // Must have at least one dot

      String[] labels = s.split("\\.");
      for (String label : labels) {
        if (label.isEmpty() || label.length() > 63) return false;
        if (label.startsWith("-") || label.endsWith("-")) return false;
        if (!label.matches("^[a-zA-Z0-9-]+$")) return false;
      }
      return true;
    }
  },

  DATE {
    @Override
    public boolean test(String s) {
      try {
        java.time.LocalDate.parse(s);
        return true;
      } catch (Exception e) {
        return false;
      }
    }
  },

  TIME {
    @Override
    public boolean test(String s) {
      try {
        // Try OffsetTime first (with timezone)
        java.time.OffsetTime.parse(s);
        return true;
      } catch (Exception e) {
        try {
          // Try LocalTime (without timezone)
          java.time.LocalTime.parse(s);
          return true;
        } catch (Exception e2) {
          return false;
        }
      }
    }
  },

  DATE_TIME {
    @Override
    public boolean test(String s) {
      try {
        // Try OffsetDateTime first (with timezone)
        java.time.OffsetDateTime.parse(s);
        return true;
      } catch (Exception e) {
        try {
          // Try LocalDateTime (without timezone)
          java.time.LocalDateTime.parse(s);
          return true;
        } catch (Exception e2) {
          return false;
        }
      }
    }
  },

  REGEX {
    @Override
    public boolean test(String s) {
      try {
        java.util.regex.Pattern.compile(s);
        return true;
      } catch (Exception e) {
        return false;
      }
    }
  };

  /// Get format validator by name (case-insensitive)
  static FormatValidator byName(String name) {
    try {
      return Format.valueOf(name.toUpperCase().replace("-", "_"));
    } catch (IllegalArgumentException e) {
      return null; // Unknown format
    }
  }
}
