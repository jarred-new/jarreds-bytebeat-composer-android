package com.jarredapps.bytebeatcomposer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;

public class BBFileProvider extends ContentProvider {

    private static final String AUTHORITY = "com.jarredapps.provider";
    private static final int ITEMS = 1;
    private static final int ITEM_ID = 2;

    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        uriMatcher.addURI(AUTHORITY, "bb", ITEMS);
        uriMatcher.addURI(AUTHORITY, "bb/#", ITEM_ID);
    }

    @Override
    public String getType(Uri uri) {
        switch (uriMatcher.match(uri)) {
            case ITEMS:
                // Multiple records
                return "vnd.android.cursor.dir/vnd.jarredapps.bb";
            case ITEM_ID:
                // Single record
                return "vnd.android.cursor.item/vnd.jarredapps.bb";
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }

    // Implement required CRUD lifecycle methods below (onCreate, query, insert, etc.)
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri u, String[] s, String o, String[] r, String g) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] r) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] r) { return 0; }
}