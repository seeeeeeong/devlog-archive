INSERT INTO blogs (name, company, rss_url, home_url)
SELECT 'NHN Cloud Meetup', 'NHN', 'https://meetup.nhncloud.com/rss', 'https://meetup.nhncloud.com'
WHERE NOT EXISTS (
    SELECT 1 FROM blogs WHERE home_url = 'https://meetup.nhncloud.com'
);

INSERT INTO blogs (name, company, rss_url, home_url)
SELECT '카카오페이 기술블로그', '카카오페이', 'https://tech.kakaopay.com/rss', 'https://tech.kakaopay.com'
WHERE NOT EXISTS (
    SELECT 1 FROM blogs WHERE home_url = 'https://tech.kakaopay.com'
);

INSERT INTO blogs (name, company, rss_url, home_url)
SELECT '여기어때 기술블로그', '여기어때', 'https://techblog.gccompany.co.kr/feed', 'https://techblog.gccompany.co.kr'
WHERE NOT EXISTS (
    SELECT 1 FROM blogs WHERE home_url = 'https://techblog.gccompany.co.kr'
);

INSERT INTO blogs (name, company, rss_url, home_url)
SELECT '스포카 기술블로그', '스포카', 'https://spoqa.github.io/atom.xml', 'https://spoqa.github.io'
WHERE NOT EXISTS (
    SELECT 1 FROM blogs WHERE home_url = 'https://spoqa.github.io'
);

INSERT INTO blogs (name, company, rss_url, home_url)
SELECT '리디 기술블로그', '리디', 'https://ridicorp.com/feed/', 'https://ridicorp.com'
WHERE NOT EXISTS (
    SELECT 1 FROM blogs WHERE home_url = 'https://ridicorp.com'
);

INSERT INTO blogs (name, company, rss_url, home_url)
SELECT '직방 기술블로그', '직방', 'https://medium.com/feed/zigbang', 'https://medium.com/zigbang'
WHERE NOT EXISTS (
    SELECT 1 FROM blogs WHERE home_url = 'https://medium.com/zigbang'
);

INSERT INTO blogs (name, company, rss_url, home_url)
SELECT '하이퍼커넥트 기술블로그', '하이퍼커넥트', 'https://hyperconnect.github.io/feed.xml', 'https://hyperconnect.github.io'
WHERE NOT EXISTS (
    SELECT 1 FROM blogs WHERE home_url = 'https://hyperconnect.github.io'
);

INSERT INTO blogs (name, company, rss_url, home_url)
SELECT '넥스트리소프트 기술블로그', '넥스트리소프트', 'https://www.nextree.io/feed/', 'https://www.nextree.io'
WHERE NOT EXISTS (
    SELECT 1 FROM blogs WHERE home_url = 'https://www.nextree.io'
);
