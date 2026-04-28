package com.airplanehome.flight.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SeoRouteService {
    private final Map<String, SeoRoute> routesBySlug;

    public SeoRouteService() {
        this.routesBySlug = new LinkedHashMap<String, SeoRoute>();
        register(new SeoRoute(
            "icn-fukuoka",
            "ICN",
            "인천",
            "FUK",
            "후쿠오카",
            "Fukuoka",
            "인천-후쿠오카 항공권 가격 추적 | 최저가 알림",
            "인천에서 후쿠오카로 가는 항공권 가격 흐름을 확인하고 최저가가 내려갈 때 바로 추적할 수 있는 페이지입니다.",
            "가격이 자주 바뀌는 노선입니다. 지금 최저가를 보고 바로 추적해 두세요.",
            "짧은 일본 여행, 2박 3일 일정, 재방문 수요",
            "출발 2~6주 전부터 매일 확인",
            "후쿠오카 최저가 보기",
            Arrays.asList(
                "편도와 왕복 가격을 바로 비교할 수 있습니다.",
                "출발 시간과 항공사를 함께 확인할 수 있습니다.",
                "마음에 들면 바로 추적을 시작하면 됩니다."
            ),
            Arrays.asList(
                "후쿠오카 항공권은 언제부터 추적하는 게 좋은가요?",
                "주말 출발과 평일 출발 가격 차이가 큰 편인가요?",
                "후쿠오카 노선은 편도보다 왕복이 유리한 경우가 많나요?"
            ),
            Arrays.asList("icn-tokyo-narita", "icn-osaka-kansai", "icn-taipei")
        ));
        register(new SeoRoute(
            "icn-tokyo-narita",
            "ICN",
            "인천",
            "NRT",
            "도쿄 나리타",
            "Tokyo",
            "인천-도쿄 나리타 항공권 가격 추적 | 도쿄 특가 알림",
            "도쿄 나리타 노선의 가격 변동을 빠르게 확인하고 특가 시점에 추적을 걸 수 있는 랜딩페이지입니다.",
            "가격이 빠르게 바뀌는 노선이라 바로 추적해 두기 좋습니다.",
            "도쿄 자유여행, 쇼핑, 짧은 연차 여행",
            "출발 3~8주 전부터 집중 추적",
            "도쿄 최저가 보기",
            Arrays.asList(
                "짧은 특가가 자주 보이는 노선입니다.",
                "왕복이면 귀국편 시간까지 함께 볼 수 있습니다.",
                "지금 가격을 보고 바로 추적하기 좋습니다."
            ),
            Arrays.asList(
                "도쿄 항공권은 어느 요일 출발이 더 저렴한 편인가요?",
                "나리타 노선과 도쿄 전체 검색은 어떻게 다르게 써야 하나요?",
                "도쿄 특가는 얼마나 빨리 마감되는 편인가요?"
            ),
            Arrays.asList("icn-fukuoka", "icn-osaka-kansai", "icn-bangkok")
        ));
        register(new SeoRoute(
            "icn-osaka-kansai",
            "ICN",
            "인천",
            "KIX",
            "오사카 간사이",
            "Osaka",
            "인천-오사카 항공권 가격 추적 | 주말 특가 탐색",
            "오사카 간사이 노선을 검색 유입용으로 정리한 페이지로, 짧은 여행 수요에 맞는 CTA와 가격 추적 흐름을 제공합니다.",
            "주말 수요가 많아 날짜에 따라 가격 차이가 큰 노선입니다.",
            "먹거리 여행, 가족 단기 여행, 주말 일본 여행",
            "출발 2~5주 전 수시 체크",
            "오사카 최저가 보기",
            Arrays.asList(
                "주말 왕복 가격을 바로 확인할 수 있습니다.",
                "시간대와 항공사를 함께 볼 수 있습니다.",
                "비슷한 일본 노선과 같이 비교하기 좋습니다."
            ),
            Arrays.asList(
                "오사카 항공권은 금요일 출발이 가장 비싼가요?",
                "오사카 주말 왕복 가격은 얼마나 자주 변하나요?",
                "간사이 노선은 편도보다 왕복 추적이 더 유리한가요?"
            ),
            Arrays.asList("icn-fukuoka", "icn-tokyo-narita", "icn-bangkok")
        ));
        register(new SeoRoute(
            "icn-bangkok",
            "ICN",
            "인천",
            "BKK",
            "방콕",
            "Bangkok",
            "인천-방콕 항공권 가격 추적 | 동남아 특가 알림",
            "방콕 노선을 중심으로 동남아 장거리 주말 수요와 휴가 수요를 흡수하기 위한 SEO 랜딩페이지입니다.",
            "성수기 영향이 커서 가격 차이가 크게 나는 노선입니다.",
            "동남아 휴양, 장기 주말 여행, 가족 여행",
            "출발 4~10주 전부터 추적",
            "방콕 최저가 보기",
            Arrays.asList(
                "성수기와 비수기 가격 차이가 큽니다.",
                "왕복 일정으로 보기 좋은 노선입니다.",
                "동남아 다른 노선과 같이 비교하기 좋습니다."
            ),
            Arrays.asList(
                "방콕 항공권은 몇 달 전부터 보는 게 좋나요?",
                "연휴가 끼면 가격 상승 폭이 큰 편인가요?",
                "방콕 노선은 알림 추적 가치가 높은 편인가요?"
            ),
            Arrays.asList("icn-da-nang", "icn-singapore", "icn-ho-chi-minh")
        ));
        register(new SeoRoute(
            "icn-taipei",
            "ICN",
            "인천",
            "TPE",
            "타이베이",
            "Taipei",
            "인천-타이베이 항공권 가격 추적 | 대만 특가 확인",
            "타이베이 노선의 가격 확인과 추적 전환을 위한 대만 중심 랜딩페이지입니다.",
            "짧은 일정으로 많이 가는 노선이라 바로 추적해 두기 좋습니다.",
            "미식 여행, 가벼운 해외여행, 재방문 수요",
            "출발 2~6주 전부터 추적",
            "타이베이 최저가 보기",
            Arrays.asList(
                "짧은 일정과 주말 출발 수요가 많습니다.",
                "첫 검색 후 바로 추적하기 좋습니다.",
                "비슷한 일본 노선과 함께 많이 봅니다."
            ),
            Arrays.asList(
                "타이베이 항공권은 주말 출발 수요가 큰가요?",
                "대만 노선은 단기 특가가 자주 열리나요?",
                "타이베이 추적 페이지는 어떤 사용자에게 잘 맞나요?"
            ),
            Arrays.asList("icn-fukuoka", "icn-hong-kong", "icn-singapore")
        ));
        register(new SeoRoute(
            "icn-hong-kong",
            "ICN",
            "인천",
            "HKG",
            "홍콩",
            "Hong Kong",
            "인천-홍콩 항공권 가격 추적 | 도시 여행 최저가",
            "홍콩 도심형 여행 수요를 겨냥한 랜딩페이지로, 빠른 가격 탐색과 전환에 최적화된 구조를 제공합니다.",
            "짧은 일정으로 많이 가는 노선이라 바로 추적해 두기 좋습니다.",
            "도시 여행, 미식 여행, 짧은 휴가",
            "출발 2~7주 전부터 체크",
            "홍콩 최저가 보기",
            Arrays.asList(
                "짧은 일정 항공권을 빠르게 비교하기 좋습니다.",
                "도시 여행 목적지와 함께 보기 좋습니다.",
                "비슷한 노선과 같이 비교하기 좋습니다."
            ),
            Arrays.asList(
                "홍콩 항공권은 주중 출발이 더 저렴한 편인가요?",
                "짧은 일정일수록 편도보다 왕복 검색이 유리한가요?",
                "홍콩 노선은 알림 추적을 얼마나 길게 두는 게 좋나요?"
            ),
            Arrays.asList("icn-taipei", "icn-singapore", "icn-bangkok")
        ));
        register(new SeoRoute(
            "icn-singapore",
            "ICN",
            "인천",
            "SIN",
            "싱가포르",
            "Singapore",
            "인천-싱가포르 항공권 가격 추적 | 장거리 도시 특가",
            "싱가포르 노선 가격 추적과 예약처 이동을 함께 유도하는 장거리 도시여행용 랜딩페이지입니다.",
            "가격 범위가 넓은 편이라 미리 비교해 두기 좋은 노선입니다.",
            "도시 여행, 출장 겸 여행, 장거리 주말 여행",
            "출발 4~9주 전부터 추적",
            "싱가포르 최저가 보기",
            Arrays.asList(
                "가격 차이가 크게 벌어질 수 있습니다.",
                "출장과 여행 수요가 함께 있습니다.",
                "동남아 도시 노선과 같이 보기 좋습니다."
            ),
            Arrays.asList(
                "싱가포르 항공권은 너무 일찍 보면 의미가 없나요?",
                "장거리 도시 노선은 추적 기간을 길게 가져가야 하나요?",
                "싱가포르 노선은 어떤 CTA가 전환에 유리한가요?"
            ),
            Arrays.asList("icn-bangkok", "icn-hong-kong", "icn-da-nang")
        ));
        register(new SeoRoute(
            "icn-da-nang",
            "ICN",
            "인천",
            "DAD",
            "다낭",
            "Da Nang",
            "인천-다낭 항공권 가격 추적 | 휴양지 특가",
            "다낭 휴양 수요를 대상으로 가격 변동과 일정 탐색을 돕는 SEO 랜딩페이지입니다.",
            "휴가철에 가격이 빠르게 오를 수 있어 미리 보기 좋은 노선입니다.",
            "휴양 여행, 가족 여행, 리조트 여행",
            "출발 4~8주 전부터 추적",
            "다낭 최저가 보기",
            Arrays.asList(
                "성수기 가격 차이가 큰 편입니다.",
                "휴양 여행 항공권을 찾을 때 많이 봅니다.",
                "동남아 노선과 같이 비교하기 좋습니다."
            ),
            Arrays.asList(
                "다낭 항공권은 방학 시즌 전에 얼마나 빨리 오르나요?",
                "휴양지 노선은 목표가 설정이 유리한가요?",
                "다낭 노선은 추적 후 예약처 이동 전환이 잘 나오나요?"
            ),
            Arrays.asList("icn-bangkok", "icn-ho-chi-minh", "icn-singapore")
        ));
        register(new SeoRoute(
            "icn-ho-chi-minh",
            "ICN",
            "인천",
            "SGN",
            "호치민",
            "Ho Chi Minh City",
            "인천-호치민 항공권 가격 추적 | 베트남 도시 노선",
            "호치민 노선의 가격 탐색과 추적 전환을 위한 베트남 도시여행용 페이지입니다.",
            "여행과 출장 수요가 함께 있어 가격 차이를 자주 확인하기 좋은 노선입니다.",
            "도시 여행, 출장, 장기 체류 출발점",
            "출발 3~8주 전부터 확인",
            "호치민 최저가 보기",
            Arrays.asList(
                "도시 여행 수요가 많은 노선입니다.",
                "출장 일정과 함께 비교하기 좋습니다.",
                "왕복 일정으로 많이 확인합니다."
            ),
            Arrays.asList(
                "호치민 노선은 다낭보다 가격 예측이 어려운가요?",
                "출장 수요가 있으면 출발 임박 가격이 더 높아지나요?",
                "호치민 추적 페이지는 어떤 사용자에게 적합한가요?"
            ),
            Arrays.asList("icn-da-nang", "icn-bangkok", "icn-singapore")
        ));
        register(new SeoRoute(
            "icn-los-angeles",
            "ICN",
            "인천",
            "LAX",
            "로스앤젤레스",
            "Los Angeles",
            "인천-로스앤젤레스 항공권 가격 추적 | 장거리 미주 노선",
            "장거리 미주 노선의 높은 객단가를 겨냥해 추적과 제휴 전환 가능성을 검증하는 랜딩페이지입니다.",
            "장거리 노선이라 가격 차이가 크게 느껴지는 편입니다.",
            "장거리 가족 여행, 유학/방문, 미주 여행",
            "출발 6~12주 전부터 추적",
            "LA 최저가 보기",
            Arrays.asList(
                "장거리라 가격 차이가 크게 보일 수 있습니다.",
                "장기 일정이나 방문 일정에 많이 봅니다.",
                "미주 다른 노선과 함께 보기 좋습니다."
            ),
            Arrays.asList(
                "로스앤젤레스 항공권은 몇 달 전부터 봐야 하나요?",
                "장거리 노선은 목표가를 설정해두는 편이 유리한가요?",
                "미주 노선은 일본 노선보다 전환 구조가 어떻게 다른가요?"
            ),
            Arrays.asList("icn-san-francisco", "icn-new-york-jfk", "icn-bangkok")
        ));
        register(new SeoRoute(
            "icn-san-francisco",
            "ICN",
            "인천",
            "SFO",
            "샌프란시스코",
            "San Francisco",
            "인천-샌프란시스코 항공권 가격 추적 | 미국 서부 특가",
            "샌프란시스코 노선의 장거리 검색 의도를 포착하고 가격 추적 CTA로 연결하는 페이지입니다.",
            "출장과 여행 수요가 함께 있어 가격 차이를 꾸준히 보기 좋은 노선입니다.",
            "출장 겸 여행, 미국 서부 여행, 가족 방문",
            "출발 6~12주 전부터 추적",
            "샌프란시스코 최저가 보기",
            Arrays.asList(
                "미국 서부 노선을 비교할 때 많이 봅니다.",
                "출장 일정과 함께 보기 좋습니다.",
                "미주 다른 노선과 같이 비교하기 좋습니다."
            ),
            Arrays.asList(
                "샌프란시스코 항공권은 평일 출발이 더 유리한가요?",
                "미국 서부 노선은 얼마나 길게 추적해야 하나요?",
                "장거리 노선 랜딩페이지는 어떤 정보를 먼저 보여줘야 하나요?"
            ),
            Arrays.asList("icn-los-angeles", "icn-new-york-jfk", "icn-singapore")
        ));
        register(new SeoRoute(
            "icn-new-york-jfk",
            "ICN",
            "인천",
            "JFK",
            "뉴욕",
            "New York",
            "인천-뉴욕 항공권 가격 추적 | 장거리 최저가 모니터링",
            "뉴욕 JFK 노선의 높은 단가와 긴 탐색 기간을 고려한 장거리 SEO 랜딩페이지입니다.",
            "장거리 고가 노선이라 오래 추적해 두기 좋은 편입니다.",
            "장기 여행, 출장, 방문 수요",
            "출발 6~14주 전부터 추적",
            "뉴욕 최저가 보기",
            Arrays.asList(
                "장거리라 체감 가격 차이가 큽니다.",
                "출장과 여행 수요가 함께 있습니다.",
                "장거리 다른 노선과 함께 보기 좋습니다."
            ),
            Arrays.asList(
                "뉴욕 항공권은 언제부터 추적하는 게 가장 효율적인가요?",
                "고가 장거리 노선은 무료 추적만으로도 충분한가요?",
                "미주 동부 노선은 전환까지 시간이 오래 걸리는 편인가요?"
            ),
            Arrays.asList("icn-los-angeles", "icn-san-francisco", "icn-london-heathrow")
        ));
        register(new SeoRoute(
            "icn-london-heathrow",
            "ICN",
            "인천",
            "LHR",
            "런던 히드로",
            "London",
            "인천-런던 항공권 가격 추적 | 유럽 장거리 특가",
            "유럽 장거리 노선 수요를 잡기 위한 런던 히드로 중심 랜딩페이지입니다.",
            "장거리 유럽 노선이라 미리 가격 흐름을 보기 좋은 노선입니다.",
            "유럽 여행, 장기 일정, 성수기 장거리 여행",
            "출발 6~14주 전부터 추적",
            "런던 최저가 보기",
            Arrays.asList(
                "유럽 여행 준비 때 먼저 보기 좋습니다.",
                "장기 일정이나 성수기 여행에 많이 봅니다.",
                "유럽과 미주 장거리 노선과 같이 보기 좋습니다."
            ),
            Arrays.asList(
                "런던 항공권은 여름 성수기 전에 얼마나 빨리 추적해야 하나요?",
                "유럽 장거리 노선은 왕복 검색 전환이 더 중요한가요?",
                "런던 노선은 어떤 사용자가 추적을 오래 유지하나요?"
            ),
            Arrays.asList("icn-new-york-jfk", "icn-los-angeles", "icn-paris-charles-de-gaulle")
        ));
        register(new SeoRoute(
            "icn-paris-charles-de-gaulle",
            "ICN",
            "인천",
            "CDG",
            "파리 샤를드골",
            "Paris",
            "인천-파리 항공권 가격 추적 | 유럽 특가 탐색",
            "파리 노선의 시즌성 수요를 겨냥한 유럽 랜딩페이지로, 검색 유입과 추적 전환 구조를 검증하기 위한 페이지입니다.",
            "시즌에 따라 가격 차이가 커서 미리 비교해 두기 좋은 노선입니다.",
            "유럽 자유여행, 허니문, 장기 일정",
            "출발 6~14주 전부터 추적",
            "파리 최저가 보기",
            Arrays.asList(
                "성수기와 비수기 차이가 큽니다.",
                "유럽 자유여행 준비에 많이 봅니다.",
                "런던과 함께 비교하기 좋습니다."
            ),
            Arrays.asList(
                "파리 항공권은 시즌별 가격 차이가 큰 편인가요?",
                "유럽 여행은 목표가를 어떻게 잡는 게 좋나요?",
                "파리 노선 랜딩페이지에는 어떤 정보가 우선이어야 하나요?"
            ),
            Arrays.asList("icn-london-heathrow", "icn-new-york-jfk", "icn-bangkok")
        ));
    }

    public List<SeoRoute> getRoutes() {
        return new ArrayList<SeoRoute>(routesBySlug.values());
    }

    public SeoRoute getRoute(String slug) {
        return routesBySlug.get(slug);
    }

    public List<SeoRoute> getRelatedRoutes(SeoRoute route) {
        List<SeoRoute> related = new ArrayList<SeoRoute>();
        for (String slug : route.getRelatedSlugs()) {
            SeoRoute candidate = getRoute(slug);
            if (candidate != null) {
                related.add(candidate);
            }
        }
        return related;
    }

    private void register(SeoRoute route) {
        routesBySlug.put(route.getSlug(), route);
    }
}
