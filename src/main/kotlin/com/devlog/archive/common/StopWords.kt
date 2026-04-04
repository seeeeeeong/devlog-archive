package com.devlog.archive.common

object StopWords {
    val set: Set<String> = setOf(
        // English
        "the", "and", "for", "with", "from", "that", "this", "into", "about", "have", "has",
        "how", "what", "when", "where", "will", "your", "post", "blog", "code", "using", "use",
        "guide", "story", "engineering", "system", "service", "platform", "team", "tech",
        // Korean
        "에서", "으로", "하다", "하는", "했다", "대한", "관련", "정리", "구현", "문제", "해결", "개발", "적용",
        "기능", "구조", "설계", "이슈", "트러블", "슈팅", "사용", "방법", "이렇게", "이유", "운영기", "구축기",
    )
}
