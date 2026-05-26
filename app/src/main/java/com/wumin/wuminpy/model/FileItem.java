package com.wumin.wuminpy.model;

import android.os.Parcel;
import android.os.Parcelable;

public class FileItem implements Parcelable {
    private final String name;
    private final String path;
    private final boolean isDirectory;
    private final int iconRes;
    private final long size;
    private final long lastModified;

    public FileItem(String name, String path, boolean isDirectory, int iconRes, long size, long lastModified) {
        this.name = name;
        this.path = path;
        this.isDirectory = isDirectory;
        this.iconRes = iconRes;
        this.size = size;
        this.lastModified = lastModified;
    }

    protected FileItem(Parcel in) {
        name = in.readString();
        path = in.readString();
        isDirectory = in.readByte() != 0;
        iconRes = in.readInt();
        size = in.readLong();
        lastModified = in.readLong();
    }

    public static final Creator<FileItem> CREATOR = new Creator<FileItem>() {
        @Override
        public FileItem createFromParcel(Parcel in) {
            return new FileItem(in);
        }

        @Override
        public FileItem[] newArray(int size) {
            return new FileItem[size];
        }
    };

    public String getName() { return name; }
    public String getPath() { return path; }
    public boolean isDirectory() { return isDirectory; }
    public int getIconRes() { return iconRes; }
    public long getSize() { return size; }
    public long getLastModified() { return lastModified; }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(path);
        dest.writeByte((byte) (isDirectory ? 1 : 0));
        dest.writeInt(iconRes);
        dest.writeLong(size);
        dest.writeLong(lastModified);
    }
}
