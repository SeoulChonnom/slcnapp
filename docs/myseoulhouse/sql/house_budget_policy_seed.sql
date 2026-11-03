-- 내집찾기 예산 정책 시드. 배포 시 1회 수동 실행한다(두 번 실행해도 안전).
-- 근거: docs/myseoulhouse/be-design.md §9.5, spec.md 9장.
-- 전제: 앱이 한 번 기동되어 slcn.house_budget_policy 테이블이 ddl-auto로 만들어져 있어야 한다.
-- JSON 키는 PriceCap / CostRates VO 필드명과 같아야 한다(HouseBudgetPolicySeedTest가 이 파일을 그대로 읽어 검증한다).
INSERT INTO slcn.house_budget_policy
  (id, entity_version, registered_time, modified_time, effective_from,
   dsr_ratio, stress_rate_pp, term_years, default_reserve_manwon, ltv_first_time, ltv_non_first,
   price_caps, cost_rates, source_note)
SELECT
   gen_random_uuid()::text, 0,
   (extract(epoch from now()) * 1000)::bigint, (extract(epoch from now()) * 1000)::bigint, DATE '2025-10-16',
   0.400, 3.00, 30, 2000, 0.700, 0.400,
   '[{"maxPriceManwon":150000,"capManwon":60000},
     {"maxPriceManwon":250000,"capManwon":40000},
     {"maxPriceManwon":null,"capManwon":20000}]',
   '{"acquisitionTax":{"lowRatePct":1.0,"lowMaxManwon":60000,"highRatePct":3.0,"highMinManwon":90000,"localEducationRatio":0.1},
     "firstTimeRelief":{"capManwon":200,"maxPriceManwon":120000},
     "brokerage":{"tiers":[{"belowManwon":5000,"ratePct":0.6,"capManwon":25},
                           {"belowManwon":20000,"ratePct":0.5,"capManwon":80},
                           {"belowManwon":90000,"ratePct":0.4,"capManwon":null},
                           {"belowManwon":120000,"ratePct":0.5,"capManwon":null},
                           {"belowManwon":150000,"ratePct":0.6,"capManwon":null},
                           {"belowManwon":null,"ratePct":0.7,"capManwon":null}],
                  "vatRatio":0.1},
     "registrationRatePct":0.2}',
   '10·15 대책(2025-10-16 시행), 지방세법, 서울시 중개보수 조례'
WHERE NOT EXISTS (SELECT 1 FROM slcn.house_budget_policy WHERE effective_from = DATE '2025-10-16');
