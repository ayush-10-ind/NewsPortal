package com.newsportal.dto;

import java.util.List;

public class NewsApiResponseDTO {

    private String status;

    private int totalResults;

    private List<NewsApiArticleDTO> articles;


    public String getStatus() {
        return status;
    }


    public void setStatus(String status) {
        this.status = status;
    }


    public int getTotalResults() {
        return totalResults;
    }


    public void setTotalResults(int totalResults) {
        this.totalResults = totalResults;
    }


    public List<NewsApiArticleDTO> getArticles() {
        return articles;
    }


    public void setArticles(
            List<NewsApiArticleDTO> articles) {

        this.articles = articles;
    }
}