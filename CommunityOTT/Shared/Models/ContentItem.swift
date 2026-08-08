//
//  ContentItem.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import Foundation

public enum ContentType: String, Codable, Hashable, CaseIterable, Sendable {
    case entertainment = "ENTERTAINMENT"
    case documentary = "DOCUMENTARY"
    case podcast = "PODCAST"
    case education = "EDUCATION"
    case story = "STORY"
}

public struct ContentCategory: Identifiable, Codable, Hashable, Sendable {
    public let id: String
    public let title: String
    public let description: String
    public let imageName: String
    public let type: ContentType
    
    public init(id: String, title: String, description: String = "", imageName: String = "hero_heritage", type: ContentType = .documentary) {
        self.id = id
        self.title = title
        self.description = description
        self.imageName = imageName
        self.type = type
    }
    
    public static let allCategories: [ContentCategory] = [
        ContentCategory(
            id: "folk_culture",
            title: "Folk & Cultural Arts",
            description: "Explore traditional performances, heritage crafts, and living folk legends.",
            imageName: "cover_folk",
            type: .story
        ),
        ContentCategory(
            id: "history_docs",
            title: "History & Documentaries",
            description: "Deep-dive documentaries into forgotten histories and timeless monuments.",
            imageName: "hero_heritage",
            type: .documentary
        ),
        ContentCategory(
            id: "empowerment",
            title: "Empowerment",
            description: "Inspiring initiatives uplifting local communities and grassroots leaders.",
            imageName: "cover_weaving",
            type: .story
        ),
        ContentCategory(
            id: "voices_success",
            title: "Voices of Success",
            description: "First-hand accounts from community leaders, innovators, and changemakers.",
            imageName: "cover_podcast",
            type: .story
        ),
        ContentCategory(
            id: "podcasts",
            title: "Podcasts",
            description: "Thoughtful audio conversations and storytelling from diverse community voices.",
            imageName: "cover_podcast",
            type: .podcast
        ),
        ContentCategory(
            id: "education_skills",
            title: "Education & Skills",
            description: "Practical workshops, entrepreneurial wisdom, and skill-building guides.",
            imageName: "cover_weaving",
            type: .education
        )
    ]
}

public struct ContentItem: Identifiable, Codable, Hashable, Sendable {
    public let id: String
    public let title: String
    public let description: String
    public let category: String
    public let type: ContentType
    public let posterURL: String?
    public let bannerURL: String?
    public let videoURL: String?
    public let durationInSeconds: Int?
    public let progress: Double? // 0.0 to 1.0 for Continue Watching
    public let isFeatured: Bool
    public let releaseYear: Int
    public let language: String
    public let imageName: String?
    
    public init(
        id: String,
        title: String,
        description: String,
        category: String,
        type: ContentType,
        posterURL: String? = nil,
        bannerURL: String? = nil,
        videoURL: String? = nil,
        durationInSeconds: Int? = 2520, // default 42m
        progress: Double? = nil,
        isFeatured: Bool = false,
        releaseYear: Int = 2026,
        language: String = "English",
        imageName: String? = nil
    ) {
        self.id = id
        self.title = title
        self.description = description
        self.category = category
        self.type = type
        self.posterURL = posterURL
        self.bannerURL = bannerURL
        self.videoURL = videoURL
        self.durationInSeconds = durationInSeconds
        self.progress = progress
        self.isFeatured = isFeatured
        self.releaseYear = releaseYear
        self.language = language
        self.imageName = imageName
    }
    
    public var durationFormatted: String {
        guard let durationInSeconds else { return "42m" }
        let minutes = durationInSeconds / 60
        return "\(minutes)m"
    }
    
    public var subtitleMetadata: String {
        "\(category) • \(durationFormatted)"
    }
    
    public func withProgress(_ newProgress: Double?) -> ContentItem {
        ContentItem(
            id: id,
            title: title,
            description: description,
            category: category,
            type: type,
            posterURL: posterURL,
            bannerURL: bannerURL,
            videoURL: videoURL,
            durationInSeconds: durationInSeconds,
            progress: newProgress,
            isFeatured: isFeatured,
            releaseYear: releaseYear,
            language: language,
            imageName: imageName
        )
    }
}
