package com.agent.entity;

import lombok.Data;

import java.util.List;

@Data
public class MovieReview {
    private String title;
    private int rating;  // 1-10
    private String summary;
    private List<String> pros;
    private List<String> cons;
}
