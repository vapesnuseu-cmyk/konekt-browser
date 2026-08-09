/* Minimal ContentProvider that hands the downloaded update APK to the
   system package installer as a content:// URI. Framework-only — avoids
   the androidx FileProvider dependency. */
package network.konekt.browser;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public class ApkProvider extends ContentProvider {
  static final String NAME = "konekt-update.apk";
  static File file(android.content.Context c) { return new File(c.getExternalFilesDir(null), NAME); }

  @Override public boolean onCreate() { return true; }

  @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    return ParcelFileDescriptor.open(file(getContext()), ParcelFileDescriptor.MODE_READ_ONLY);
  }

  @Override public Cursor query(Uri uri, String[] proj, String sel, String[] args, String sort) {
    File f = file(getContext());
    MatrixCursor c = new MatrixCursor(new String[]{ OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE });
    c.addRow(new Object[]{ NAME, f.length() });
    return c;
  }

  @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
  @Override public Uri insert(Uri uri, ContentValues v) { return null; }
  @Override public int delete(Uri uri, String sel, String[] args) { return 0; }
  @Override public int update(Uri uri, ContentValues v, String sel, String[] args) { return 0; }
}
