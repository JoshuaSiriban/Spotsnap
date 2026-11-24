package com.example.studyflow;

public class Photo {
    int image;        // drawable resource id, used by the sample photos
    byte[] bytes;     // full size JPEG, used by user photos
    byte[] thumb;     // small JPEG for previews (only kept while a photo is being added)
    String caption;

    public Photo(int image, String caption)
    {
        this.image = image;
        this.caption = caption;
    }

    public Photo(byte[] bytes, byte[] thumb, String caption)
    {
        this.bytes = bytes;
        this.thumb = thumb;
        this.caption = caption;
    }

    public Photo(byte[] bytes, String caption)
    {
        this(bytes, null, caption);
    }

}