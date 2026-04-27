package jez.lastfleetprotocol.prototype.utils

/**
 * Sanitises a user-supplied display name into a string safe to use as the
 * stem of a JSON filename in app-data. Any character outside the alphanumeric,
 * underscore, hyphen, or space class is replaced with a single underscore.
 *
 * Spaces are deliberately allowed because user-facing design and scenario
 * names commonly contain them (e.g. `"Heavy Cruiser"`). Repositories that
 * key on identifiers rather than display names (like `FileItemLibraryRepository`)
 * should keep their own stricter sanitiser — IDs disallow spaces.
 */
fun sanitizeFilenameStem(name: String): String =
    name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
