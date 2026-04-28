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
            "주말 여행 수요가 많은 노선이라 출발 2~6주 전 가격 변동을 꾸준히 보는 편이 유리합니다.",
            "짧은 일본 여행, 2박 3일 일정, 재방문 수요",
            "출발 2~6주 전부터 매일 확인",
            "후쿠오카 가격 추적 시작",
            Arrays.asList(
                "편도와 왕복 가격을 같은 화면에서 확인할 수 있습니다.",
                "출발 시간대와 항공사를 함께 보고 바로 추적을 시작할 수 있습니다.",
                "특가가 풀리면 추적 페이지에서 가장 먼저 다시 확인하기 좋습니다."
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
            "도쿄는 검색 수요가 매우 높아 짧은 특가 창이 자주 생깁니다. 검색 후 바로 추적으로 전환하는 구조가 중요합니다.",
            "도쿄 자유여행, 쇼핑, 짧은 연차 여행",
            "출발 3~8주 전부터 집중 추적",
            "도쿄 항공권 보기",
            Arrays.asList(
                "검색량이 큰 노선이라 가격 비교와 추적 전환이 빠릅니다.",
                "왕복 일정 검색 시 귀국편 시간까지 함께 확인할 수 있습니다.",
                "도쿄 전체 수요와 나리타 중심 수요를 분리해 보기에 좋습니다."
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
            "오사카는 주말 수요 비중이 높아 출발 요일과 귀국 요일에 따라 체감 가격 차이가 큽니다.",
            "먹거리 여행, 가족 단기 여행, 주말 일본 여행",
            "출발 2~5주 전 수시 체크",
            "오사카 최저가 찾기",
            Arrays.asList(
                "주말 왕복 수요가 많아 왕복 검색 진입점으로 적합합니다.",
                "시간대와 항공사를 함께 보는 사용자가 많습니다.",
                "후쿠오카, 도쿄와 내부 링크를 연결하기 좋은 허브 노선입니다."
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
            "연휴와 성수기 영향을 많이 받는 노선이라 단기 특가보다 추적 유지가 중요합니다.",
            "동남아 휴양, 장기 주말 여행, 가족 여행",
            "출발 4~10주 전부터 추적",
            "방콕 가격 흐름 확인",
            Arrays.asList(
                "성수기와 비수기 차이가 커 장기 추적 가치가 있습니다.",
                "왕복 검색 전환에 적합한 설명형 랜딩페이지입니다.",
                "동남아 다른 목적지와 묶어 내부 링크 허브로 쓰기 좋습니다."
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
            "짧은 체류와 재방문 수요가 많아 사용자가 검색 직후 추적을 거는 비율이 높은 노선입니다.",
            "미식 여행, 가벼운 해외여행, 재방문 수요",
            "출발 2~6주 전부터 추적",
            "타이베이 특가 보기",
            Arrays.asList(
                "짧은 일정과 주말 수요가 많은 노선입니다.",
                "첫 검색에서 바로 추적으로 이어지기 쉬운 목적지입니다.",
                "도쿄, 후쿠오카와 비슷한 탐색형 퍼널로 운영할 수 있습니다."
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
            "도시 여행 수요가 높아 짧은 일정 사용자에게 추적 메시지가 잘 먹히는 노선입니다.",
            "도시 여행, 미식 여행, 짧은 휴가",
            "출발 2~7주 전부터 체크",
            "홍콩 항공권 찾기",
            Arrays.asList(
                "짧은 체류형 사용자에게 CTA 반응이 좋습니다.",
                "일본 노선 다음 단계 확장용으로 적합합니다.",
                "타이베이, 싱가포르와 함께 도시여행 카테고리를 만들기 좋습니다."
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
            "도시형 장거리 노선이라 조기 예약 수요와 마지막 특가 수요가 함께 존재합니다.",
            "도시 여행, 출장 겸 여행, 장거리 주말 여행",
            "출발 4~9주 전부터 추적",
            "싱가포르 가격 확인",
            Arrays.asList(
                "가격 변동 폭이 일본 노선보다 크고 의사결정 기간도 깁니다.",
                "동남아 고가 노선군에서 유료 알림 전환 후보가 될 수 있습니다.",
                "방콕, 홍콩과 묶어 도시형 동남아 카테고리를 만들기 좋습니다."
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
            "휴양지 성격이 강해 연휴와 방학 시즌의 가격 반응이 빠른 편입니다.",
            "휴양 여행, 가족 여행, 리조트 여행",
            "출발 4~8주 전부터 추적",
            "다낭 최저가 보기",
            Arrays.asList(
                "장기 휴가와 연휴 수요가 많아 검색 의도가 명확합니다.",
                "방콕, 세부와 함께 휴양지 카테고리를 확장하기 좋습니다.",
                "알림 기반 재방문 유도 효과가 큰 노선입니다."
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
            "출장 수요와 여행 수요가 함께 있어 가격 패턴이 단순하지 않은 편입니다.",
            "도시 여행, 출장, 장기 체류 출발점",
            "출발 3~8주 전부터 확인",
            "호치민 가격 추적",
            Arrays.asList(
                "도시형 베트남 노선으로 다낭과 수요층이 다릅니다.",
                "출장성 검색 유입도 흡수할 수 있습니다.",
                "중간 체류형 사용자의 왕복 검색 전환에 적합합니다."
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
            "객단가가 높아 전환당 수익 잠재력이 크지만 의사결정 기간도 길기 때문에 콘텐츠 신뢰도가 중요합니다.",
            "장거리 가족 여행, 유학/방문, 미주 여행",
            "출발 6~12주 전부터 추적",
            "LA 항공권 가격 보기",
            Arrays.asList(
                "장거리라 알림과 재방문 기능의 체감 가치가 큽니다.",
                "미주 카테고리 확장 시 대표 허브가 될 수 있습니다.",
                "직접 발권보다 제휴 클릭 모델 검증에 먼저 적합합니다."
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
            "기술 출장과 관광 수요가 섞여 있어 일정 분포가 넓은 편입니다.",
            "출장 겸 여행, 미국 서부 여행, 가족 방문",
            "출발 6~12주 전부터 추적",
            "샌프란시스코 가격 확인",
            Arrays.asList(
                "미주 서부 노선 비교 허브로 활용할 수 있습니다.",
                "장거리 노선 특성상 설명형 콘텐츠가 잘 맞습니다.",
                "검색 유입 후 장기 추적 저장 전환을 기대할 수 있습니다."
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
            "장거리 고가 노선이라 특가가 아니어도 추적 유지 가치가 높습니다.",
            "장기 여행, 출장, 방문 수요",
            "출발 6~14주 전부터 추적",
            "뉴욕 항공권 추적",
            Arrays.asList(
                "미주 동부 대표 노선으로 고가 검색 유입을 잡을 수 있습니다.",
                "장기 추적과 목표가 기능을 설명하기 좋습니다.",
                "유료 기능으로 확장할 때 우선 후보가 될 수 있습니다."
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
            "유럽 노선은 일정이 길고 객단가가 높아 비교와 추적 경험을 분리해 설계하는 편이 좋습니다.",
            "유럽 여행, 장기 일정, 성수기 장거리 여행",
            "출발 6~14주 전부터 추적",
            "런던 가격 흐름 확인",
            Arrays.asList(
                "유럽 대표 노선으로 검색 유입 확장성이 좋습니다.",
                "장거리 일정 설계형 콘텐츠를 붙이기 좋습니다.",
                "미주와 다른 성수기 패턴을 가진 테스트 노선입니다."
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
            "유럽 자유여행 수요가 몰리는 시즌에는 콘텐츠 신뢰도와 목표가 안내가 중요합니다.",
            "유럽 자유여행, 허니문, 장기 일정",
            "출발 6~14주 전부터 추적",
            "파리 최저가 보기",
            Arrays.asList(
                "유럽 대표 관광 노선으로 콘텐츠 확장성이 큽니다.",
                "성수기 대비형 정보 콘텐츠와 잘 맞습니다.",
                "런던과 묶어 유럽 카테고리 허브를 만들 수 있습니다."
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
