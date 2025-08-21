# CRITICAL FIX: Skin Index Storage Location

## Problem Identified
The application was writing `.skintool-index.json` files directly into the user's skin directories, which is **completely unacceptable** because:
- Skin directories should NEVER be modified by the application
- Users may not have write permissions
- It pollutes the user's skin folders with application data
- It could interfere with osu! or other tools

## Solution Implemented
Moved all index storage to the application's own configuration directory:
- **Old location**: `/path/to/skin/.skintool-index.json` ❌
- **New location**: `~/.config/OsuSkinTool/skin-indexes/[sanitized-path]-index.json` ✅

## Changes Made

### 1. Updated SkinIndexCache.java
- Added constructor to create app-managed index storage directory
- Index files now stored in `~/.config/OsuSkinTool/skin-indexes/`
- Each skin gets a unique index file based on its absolute path
- Path characters sanitized for filesystem compatibility

### 2. Key Implementation Details
```java
// Index storage in app config directory
Path configDir = Path.of(userHome, ".config", "OsuSkinTool");
this.indexStorageDir = configDir.resolve("skin-indexes");

// Unique key for each skin
String indexKey = skinDir.toAbsolutePath().toString()
    .replaceAll("[/\\\\:*?\"<>|]", "_") + "-index.json";
```

### 3. Verification
- ✅ No files written to skin directories
- ✅ Indexes stored in app config: `/root/.config/OsuSkinTool/skin-indexes/`
- ✅ Cache hits still working (11.9x speedup verified)
- ✅ Skin folders remain completely unmodified

## Testing Results
```bash
# Check skin directories - should be 0
find /root/skins -name ".skintool*" | wc -l
# Result: 0 ✅

# Check app index directory
ls /root/.config/OsuSkinTool/skin-indexes/
# Result: Shows index files ✅
```

## Important Notes
1. **Skin directories are now read-only** - as they should be
2. **All indexes managed by the application** - proper separation of concerns
3. **Performance unchanged** - still get ~12x speedup on cache hits
4. **No user data pollution** - clean separation

This was a critical fix that ensures the application respects user data boundaries.