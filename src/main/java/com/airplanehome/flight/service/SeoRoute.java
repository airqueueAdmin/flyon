package com.airplanehome.flight.service;

import java.util.List;

public class SeoRoute {
    private final String slug;
    private final String originCode;
    private final String originCityKorean;
    private final String destinationCode;
    private final String destinationCityKorean;
    private final String destinationCityEnglish;
    private final String title;
    private final String description;
    private final String heroSummary;
    private final String bestFor;
    private final String bookingWindow;
    private final String ctaLabel;
    private final List<String> highlights;
    private final List<String> faqQuestions;
    private final List<String> relatedSlugs;

    public SeoRoute(
        String slug,
        String originCode,
        String originCityKorean,
        String destinationCode,
        String destinationCityKorean,
        String destinationCityEnglish,
        String title,
        String description,
        String heroSummary,
        String bestFor,
        String bookingWindow,
        String ctaLabel,
        List<String> highlights,
        List<String> faqQuestions,
        List<String> relatedSlugs
    ) {
        this.slug = slug;
        this.originCode = originCode;
        this.originCityKorean = originCityKorean;
        this.destinationCode = destinationCode;
        this.destinationCityKorean = destinationCityKorean;
        this.destinationCityEnglish = destinationCityEnglish;
        this.title = title;
        this.description = description;
        this.heroSummary = heroSummary;
        this.bestFor = bestFor;
        this.bookingWindow = bookingWindow;
        this.ctaLabel = ctaLabel;
        this.highlights = highlights;
        this.faqQuestions = faqQuestions;
        this.relatedSlugs = relatedSlugs;
    }

    public String getSlug() {
        return slug;
    }

    public String getOriginCode() {
        return originCode;
    }

    public String getOriginCityKorean() {
        return originCityKorean;
    }

    public String getDestinationCode() {
        return destinationCode;
    }

    public String getDestinationCityKorean() {
        return destinationCityKorean;
    }

    public String getDestinationCityEnglish() {
        return destinationCityEnglish;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getHeroSummary() {
        return heroSummary;
    }

    public String getBestFor() {
        return bestFor;
    }

    public String getBookingWindow() {
        return bookingWindow;
    }

    public String getCtaLabel() {
        return ctaLabel;
    }

    public List<String> getHighlights() {
        return highlights;
    }

    public List<String> getFaqQuestions() {
        return faqQuestions;
    }

    public List<String> getRelatedSlugs() {
        return relatedSlugs;
    }

    public String getRouteLabel() {
        return originCityKorean + " (" + originCode + ") -> "
            + destinationCityKorean + " (" + destinationCode + ")";
    }

    public String getSearchUrl() {
        return "/?origin=" + originCode + "&destination=" + destinationCode;
    }
}
