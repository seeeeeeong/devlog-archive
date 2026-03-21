INSERT INTO blogs (name, company, rss_url, home_url)
SELECT '컬리 기술 블로그', '컬리', 'https://helloworld.kurly.com/feed.xml', 'https://helloworld.kurly.com'
WHERE NOT EXISTS (
    SELECT 1 FROM blogs WHERE home_url = 'https://helloworld.kurly.com'
);

INSERT INTO blogs (name, company, rss_url, home_url)
SELECT 'LY Corporation Tech Blog', 'LY', 'https://techblog.lycorp.co.jp/ko/feed/index.xml', 'https://techblog.lycorp.co.jp/ko'
WHERE NOT EXISTS (
    SELECT 1 FROM blogs WHERE home_url = 'https://techblog.lycorp.co.jp/ko'
);

INSERT INTO blogs (name, company, rss_url, home_url)
SELECT '데브시스터즈 테크 블로그', '데브시스터즈', 'https://tech.devsisters.com/rss.xml', 'https://tech.devsisters.com'
WHERE NOT EXISTS (
    SELECT 1 FROM blogs WHERE home_url = 'https://tech.devsisters.com'
);

INSERT INTO blogs (name, company, rss_url, home_url)
SELECT '요기요 기술블로그', '요기요', 'https://techblog.yogiyo.co.kr/feed', 'https://techblog.yogiyo.co.kr'
WHERE NOT EXISTS (
    SELECT 1 FROM blogs WHERE home_url = 'https://techblog.yogiyo.co.kr'
);

INSERT INTO blogs (name, company, rss_url, home_url)
SELECT 'MUSINSA tech', '무신사', 'https://medium.com/feed/musinsa-tech', 'https://medium.com/musinsa-tech'
WHERE NOT EXISTS (
    SELECT 1 FROM blogs WHERE home_url = 'https://medium.com/musinsa-tech'
);

INSERT INTO blogs (name, company, rss_url, home_url)
SELECT 'Wanted Team', '원티드', 'https://medium.com/feed/wantedjobs', 'https://medium.com/wantedjobs'
WHERE NOT EXISTS (
    SELECT 1 FROM blogs WHERE home_url = 'https://medium.com/wantedjobs'
);
