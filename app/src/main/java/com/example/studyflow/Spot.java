package com.example.studyflow;

import java.util.ArrayList;

public class Spot {

    private String name;
    private String creator;
    private String review;

    private String category;
    public ArrayList<Photo> images;
    private double latitude;
    private double longitude;

    private int likes;

    private int saves;

    private String id;            // Firestore document id (null until the spot is saved)
    private String creatorId;     // Firebase uid of the user who pinned the spot
    private boolean isPublic = true;
    private int photoCount;       // number of pictures saved in Firestore
    private byte[] thumbnail;     // small preview picture (for list screens)

    public Spot(String name, String creator, String review, String category, double latitude, double longitude, ArrayList<Photo> images, int likes, int saves)
    {
        this.name = name;
        this.creator = creator;
        this.review = review;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.images = images;
        this.likes = likes;
        this.saves = saves;
    }

    public void setName(String name){
        this.name = name;
    }

    public void setReview(String review){
        this.review = review;
    }

    public void setCategory(String category){
        this.category = category;
    }

    public void setLocation(double latitude, double longitude){
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude(){
        return latitude;
    }

    public double getLongitude(){
        return longitude;
    }

    public String getName(){
        return name;
    }

    public String getCreator(){
        return creator;
    }

    public String getReview(){
        return review;
    }

    public String getCategory(){
        return category;
    }

    public int getLikes(){
        return likes;
    }

    public int getSaves(){
        return saves;
    }

    public String getId(){
        return id;
    }

    public void setId(String id){
        this.id = id;
    }

    public String getCreatorId(){
        return creatorId;
    }

    public void setCreatorId(String creatorId){
        this.creatorId = creatorId;
    }

    public boolean isPublic(){
        return isPublic;
    }

    public void setPublic(boolean isPublic){
        this.isPublic = isPublic;
    }

    public int getPhotoCount(){
        return photoCount;
    }

    public void setPhotoCount(int photoCount){
        this.photoCount = photoCount;
    }

    public byte[] getThumbnail(){
        return thumbnail;
    }

    public void setThumbnail(byte[] thumbnail){
        this.thumbnail = thumbnail;
    }
}