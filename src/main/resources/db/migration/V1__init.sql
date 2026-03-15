-- pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- 수집 대상 블로그
CREATE TABLE blogs (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    company    VARCHAR(100) NOT NULL,
    rss_url    VARCHAR(500) NOT NULL,
    home_url   VARCHAR(500) NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 수집된 아티클 메타
CREATE TABLE articles (
    id           BIGSERIAL PRIMARY KEY,
    blog_id      BIGINT NOT NULL REFERENCES blogs(id),
    title        VARCHAR(500) NOT NULL,
    url          TEXT NOT NULL,
    url_hash     VARCHAR(64) NOT NULL,
    summary      TEXT,
    published_at TIMESTAMP,
    crawled_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_articles_url_hash ON articles(url_hash);
CREATE INDEX idx_articles_blog_id ON articles(blog_id);
CREATE INDEX idx_articles_published_at ON articles(published_at DESC);

-- 임베딩 벡터 저장
CREATE TABLE article_embeddings (
    article_id BIGINT PRIMARY KEY REFERENCES articles(id) ON DELETE CASCADE,
    embedding  vector(1536) NOT NULL
);

-- 크롤링 이력
CREATE TABLE crawl_logs (
    id          BIGSERIAL PRIMARY KEY,
    blog_id     BIGINT NOT NULL REFERENCES blogs(id),
    started_at  TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    new_count   INT NOT NULL DEFAULT 0,
    status      VARCHAR(20) NOT NULL,  -- SUCCESS / PARTIAL / FAIL
    message     TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_crawl_logs_blog_id ON crawl_logs(blog_id);
CREATE INDEX idx_crawl_logs_started_at ON crawl_logs(started_at DESC);

-- 초기 블로그 데이터
INSERT INTO blogs (name, company, rss_url, home_url) VALUES
    ('toss tech blog',        '토스',         'https://toss.tech/rss.xml',                                   'https://toss.tech'),
    ('우아한형제들 기술블로그',  '우아한형제들',  'https://techblog.woowahan.com/feed/',                          'https://techblog.woowahan.com'),
    ('카카오 기술블로그',       '카카오',        'https://tech.kakao.com/feed/',                                'https://tech.kakao.com'),
    ('NAVER D2',              '네이버',        'https://d2.naver.com/d2.atom',                                'https://d2.naver.com'),
    ('Coupang Engineering',   '쿠팡',          'https://medium.com/feed/coupang-engineering',                 'https://medium.com/coupang-engineering'),
    ('당근 팀 블로그',          '당근',          'https://medium.com/feed/daangn',                              'https://medium.com/daangn'),
    ('LINE Engineering',      '라인',          'https://engineering.linecorp.com/ko/feed',                    'https://engineering.linecorp.com/ko'),
    ('올리브영 기술블로그',      '올리브영',      'https://oliveyoung.tech/feed.xml',                            'https://oliveyoung.tech'),
    ('뱅크샐러드 기술블로그',    '뱅크샐러드',    'https://blog.banksalad.com/rss.xml',                          'https://blog.banksalad.com'),
    ('29CM 기술블로그',         '29CM',         'https://medium.com/feed/29cm',                               'https://medium.com/29cm');
