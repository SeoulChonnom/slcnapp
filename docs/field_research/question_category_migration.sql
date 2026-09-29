-- 질문 대분류 도입 DB 이관 스크립트 (PostgreSQL)
--
-- 대분류가 없는 버전(main)의 DB를 대분류 필수 버전으로 옮긴다.
-- 새 버전은 모든 질문과 모든 답변 스냅샷에 분류가 있다고 가정하므로, 이 스크립트를 끝낸 뒤에 배포한다.
--
-- 순서
--   1. 앱을 내린다. 이관 도중 질문·매물이 새로 생기면 분류 없는 행이 다시 섞인다.
--   2. 아래 [사전 조회]로 질문 목록을 확인하고, [입력 1] 분류와 [입력 2] 질문→분류 대응을 채운다.
--   3. 스크립트 전체를 실행한다. 하나의 트랜잭션이라 검증에 실패하면 아무것도 바뀌지 않는다.
--   4. 새 버전을 배포한다. ddl-auto=update는 여기서 만든 테이블·컬럼·인덱스를 그대로 쓴다.
--
-- [사전 조회] 스크립트와 별도로 먼저 실행해 대응표를 만든다.
--   SELECT id, enabled, sort_order, versions FROM slcn.inspection_question ORDER BY sort_order, id;
--   SELECT count(*) FROM slcn.viewed_property WHERE answers IS NOT NULL AND answers <> '[]';

BEGIN;

-- 1) 분류 테이블. 이름·타입·제약은 InspectionQuestionCategoryJpo를 ddl-auto가 만든 결과와 같다.
CREATE TABLE IF NOT EXISTS slcn.inspection_question_category (
	id              varchar(255) PRIMARY KEY,
	entity_version  bigint       NOT NULL,
	modified_time   bigint,
	registered_time bigint,
	enabled         boolean      NOT NULL,
	name            varchar(50)  NOT NULL,
	sort_order      integer      NOT NULL,
	CONSTRAINT uk_inspection_question_category_name UNIQUE (name)
);
CREATE INDEX IF NOT EXISTS idx_inspection_question_category_enabled_sort
	ON slcn.inspection_question_category (enabled, sort_order);

-- 2) [입력 1] 분류. id는 INSPECTION_QUESTION_CATEGORY-{4자리 16진수}이고 0001부터 차례로 쓴다.
INSERT INTO slcn.inspection_question_category
	(id, entity_version, registered_time, modified_time, enabled, name, sort_order)
SELECT c.id, 0, now_ms, now_ms, true, c.name, c.sort_order
FROM (VALUES
	('INSPECTION_QUESTION_CATEGORY-0001', '채광·환기', 1),
	('INSPECTION_QUESTION_CATEGORY-0002', '구조', 2)
) AS c(id, name, sort_order),
	(SELECT (extract(epoch FROM now()) * 1000)::bigint AS now_ms) AS t;

-- 3) 채번 시퀀스를 위에서 쓴 마지막 id에 맞춘다. 앱이 다음 분류를 0003(16진수)부터 만든다.
INSERT INTO slcn.id_sequence (name, last_id)
SELECT 'INSPECTION_QUESTION_CATEGORY',
	lpad(to_hex(max(('x' || lpad(split_part(id, '-', 2), 8, '0'))::bit(32)::int)), 4, '0')
FROM slcn.inspection_question_category
ON CONFLICT (name) DO UPDATE SET last_id = EXCLUDED.last_id;

-- 4) 질문에 분류 컬럼을 달고 [입력 2] 대응표대로 지정한다. 비활성 질문도 빠짐없이 지정한다.
--    질문 sort_order는 그대로 둔다. 이제 "분류 안에서의 순서"로 읽히지만 상대 순서는 유지된다.
ALTER TABLE slcn.inspection_question ADD COLUMN IF NOT EXISTS category_id varchar(255);

UPDATE slcn.inspection_question q
SET category_id = m.category_id
FROM (VALUES
	('INSPECTION_QUESTION-0001', 'INSPECTION_QUESTION_CATEGORY-0001'),
	('INSPECTION_QUESTION-0002', 'INSPECTION_QUESTION_CATEGORY-0002')
) AS m(question_id, category_id)
WHERE q.id = m.question_id;

DO $$
DECLARE
	missing text;
BEGIN
	SELECT string_agg(id, ', ' ORDER BY id) INTO missing
	FROM slcn.inspection_question WHERE category_id IS NULL;
	IF missing IS NOT NULL THEN
		RAISE EXCEPTION '분류를 지정하지 않은 질문이 있다: %', missing;
	END IF;
END $$;

-- 5) 정합성 제약. NOT NULL은 ddl-auto가 기존 컬럼에 걸어 주지 않고, FK는 JPA 매핑에 없어 여기서만 건다.
ALTER TABLE slcn.inspection_question ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE slcn.inspection_question
	ADD CONSTRAINT fk_inspection_question_category
	FOREIGN KEY (category_id) REFERENCES slcn.inspection_question_category (id);
CREATE INDEX IF NOT EXISTS idx_inspection_question_category_enabled
	ON slcn.inspection_question (category_id, enabled);

-- 6) 기존 매물의 답변 스냅샷에 분류를 채운다. 이미 categoryId가 있는 답변은 건드리지 않는다.
--    배열 순서(ord)를 보존한다. 응답은 읽을 때 분류순으로 다시 정렬되므로 저장 순서는 바꾸지 않는다.
UPDATE slcn.viewed_property vp
SET answers = (
	SELECT jsonb_agg(
		CASE
			WHEN a.elem ->> 'categoryId' IS NOT NULL THEN a.elem
			ELSE a.elem || jsonb_build_object(
				'categoryId', c.id,
				'categoryName', c.name,
				'categorySortOrder', c.sort_order)
		END
		ORDER BY a.ord)::text
	FROM jsonb_array_elements(vp.answers::jsonb) WITH ORDINALITY AS a(elem, ord)
	LEFT JOIN slcn.inspection_question q ON q.id = a.elem ->> 'questionId'
	LEFT JOIN slcn.inspection_question_category c ON c.id = q.category_id
)
WHERE EXISTS (
	SELECT 1 FROM jsonb_array_elements(vp.answers::jsonb) AS e(elem)
	WHERE e.elem ->> 'categoryId' IS NULL
);

DO $$
DECLARE
	broken text;
BEGIN
	SELECT string_agg(DISTINCT vp.id, ', ') INTO broken
	FROM slcn.viewed_property vp,
		jsonb_array_elements(vp.answers::jsonb) AS e(elem)
	WHERE e.elem ->> 'categoryId' IS NULL
		OR e.elem ->> 'categoryName' IS NULL
		OR e.elem ->> 'categorySortOrder' IS NULL;
	IF broken IS NOT NULL THEN
		RAISE EXCEPTION '분류를 채우지 못한 답변이 있는 매물: %', broken;
	END IF;
END $$;

COMMIT;
