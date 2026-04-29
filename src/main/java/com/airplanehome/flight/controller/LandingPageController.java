package com.airplanehome.flight.controller;

import com.airplanehome.flight.service.SeoRoute;
import com.airplanehome.flight.service.SeoRouteService;
import com.airplanehome.flight.service.WebProperties;
import java.util.List;
import java.util.StringJoiner;
import javax.persistence.EntityNotFoundException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LandingPageController {
    private final SeoRouteService seoRouteService;
    private final WebProperties webProperties;

    public LandingPageController(SeoRouteService seoRouteService, WebProperties webProperties) {
        this.seoRouteService = seoRouteService;
        this.webProperties = webProperties;
    }

    @ModelAttribute
    public void addAnalyticsAttributes(Model model) {
        model.addAttribute("gaId", webProperties.getGaId());
        model.addAttribute("naverAnalyticsId", webProperties.getNaverAnalyticsId());
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("canonicalUrl", normalizedBaseUrl() + "/");
        model.addAttribute("pageTitle", "항공권 가격 추적 | 최저가 비교와 가격 알림");
        model.addAttribute("pageDescription", "인기 노선 최저가를 비교하고 가격 추적을 시작할 수 있는 항공권 검색 페이지입니다.");
        return "index";
    }

    @GetMapping("/tracking.html")
    public String tracking(Model model) {
        model.addAttribute("canonicalUrl", normalizedBaseUrl() + "/tracking.html");
        model.addAttribute("pageTitle", "추적 목록 | 저장한 항공권 가격 확인");
        model.addAttribute("pageDescription", "저장한 항공권 추적 목록과 최근 가격 변동을 확인하는 페이지입니다.");
        return "tracking";
    }

    @GetMapping("/routes/{slug}")
    public String routeLandingPage(@PathVariable String slug, Model model) {
        SeoRoute route = seoRouteService.getRoute(slug);
        if (route == null) {
            throw new EntityNotFoundException("해당 랜딩페이지를 찾을 수 없습니다.");
        }

        model.addAttribute("route", route);
        model.addAttribute("baseUrl", normalizedBaseUrl());
        model.addAttribute("canonicalUrl", normalizedBaseUrl() + "/routes/" + route.getSlug());
        model.addAttribute("relatedRoutes", seoRouteService.getRelatedRoutes(route));
        model.addAttribute("faqJson", buildFaqJson(route));
        return "route-landing";
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robots() {
        return "User-agent: *\n"
            + "Allow: /\n"
            + "Sitemap: " + normalizedBaseUrl() + "/sitemap.xml\n";
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap() {
        List<SeoRoute> routes = seoRouteService.getRoutes();
        StringJoiner joiner = new StringJoiner("");
        joiner.add(urlTag(normalizedBaseUrl() + "/"));
        joiner.add(urlTag(normalizedBaseUrl() + "/tracking.html"));
        for (SeoRoute route : routes) {
            joiner.add(urlTag(normalizedBaseUrl() + "/routes/" + route.getSlug()));
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">"
            + joiner
            + "</urlset>";
    }

    private String urlTag(String location) {
        return "<url><loc>" + escapeXml(location) + "</loc></url>";
    }

    private String escapeXml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private String normalizedBaseUrl() {
        String baseUrl = webProperties.getBaseUrl();
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String buildFaqJson(SeoRoute route) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"@context\":\"https://schema.org\",\"@type\":\"FAQPage\",\"mainEntity\":[");
        for (int index = 0; index < route.getFaqQuestions().size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{\"@type\":\"Question\",\"name\":\"")
                .append(escapeJson(route.getFaqQuestions().get(index)))
                .append("\",\"acceptedAnswer\":{\"@type\":\"Answer\",\"text\":\"")
                .append(escapeJson(route.getDestinationCityKorean()))
                .append(" 노선은 실시간 가격 변동이 잦아 검색 후 바로 추적을 켜 두는 방식이 유리합니다.\"}}");
        }
        builder.append("]}");
        return builder.toString();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
