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

    private long ratingSum;       // total of all star ratings (1 to 5 each)
    private int ratingCount;      // how many people rated this spot

    private String id;            // Firestore document id (null until the spot is saved)
    private String creatorId;     // Firebase uid of the user who pinned the spot
    private boolean isPublic = true;
    private int photoCount;       // number of pictures saved in Firestore
    private long createdAt;        // when it was pinned (milliseconds), 0 if unknown
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

    public void setLikes(int likes){
        this.likes = likes;
    }

    public void setSaves(int saves){
        this.saves = saves;
    }

    // ------------------------------------------------------------ ratings

    public void setRating(long ratingSum, int ratingCount){
        this.ratingSum = Math.max(0, ratingSum);
        this.ratingCount = Math.max(0, ratingCount);
    }

    public long getRatingSum(){
        return ratingSum;
    }

    public int getRatingCount(){
        return ratingCount;
    }

    public boolean hasRatings(){
        return ratingCount > 0;
    }

    /** Average stars from 1.0 to 5.0, or 0 when nobody has rated the spot yet. */
    public double getAverageRating(){
        return ratingCount == 0 ? 0 : (double) ratingSum / ratingCount;
    }

    /**
     * Used to sort "Top rated". Pulls spots with only a few ratings towards 3.5 stars,
     * so one single 5 star rating does not beat a spot with many good reviews.
     */
    public double getRankingScore(){
        final double priorWeight = 3;
        final double priorAverage = 3.5;
        return (ratingSum + priorWeight * priorAverage) / (ratingCount + priorWeight);
    }

    // ---------------------------------------------------------------------

    public long getCreatedAt(){
        return createdAt;
    }

    public void setCreatedAt(long createdAt){
        this.createdAt = createdAt;
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