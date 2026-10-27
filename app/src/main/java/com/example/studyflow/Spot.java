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
}
