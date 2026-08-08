//
//  MockContentRepository.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import Foundation

public final class MockContentRepository: ContentRepositoryProtocol, @unchecked Sendable {
    nonisolated public init() {}
    
    private let heroContent = ContentItem(
        id: "hero-1",
        title: "Stories of Heritage",
        description: "Discover stories that deserve to be remembered. Explore deep cultural traditions, living legends, and rural heritage.",
        category: "Documentary",
        type: .documentary,
        durationInSeconds: 2520, // 42m
        isFeatured: true,
        releaseYear: 2026,
        language: "Telugu",
        imageName: "hero_heritage"
    )
    
    private let continueWatchingItems: [ContentItem] = [
        ContentItem(
            id: "cw-1",
            title: "Roots of Culture: Ep 3",
            description: "Preserving ancient weaving techniques and craft secrets.",
            category: "Documentary",
            type: .documentary,
            durationInSeconds: 2880, // 48m
            progress: 0.65, // 65% completed
            releaseYear: 2026,
            language: "Telugu",
            imageName: "cover_weaving"
        ),
        ContentItem(
            id: "cw-2",
            title: "Voices of Tomorrow: Ep 7",
            description: "Empowering rural youth through digital storytelling.",
            category: "Podcast",
            type: .podcast,
            durationInSeconds: 2100, // 35m
            progress: 0.40, // 40% completed
            releaseYear: 2026,
            language: "English",
            imageName: "cover_podcast"
        ),
        ContentItem(
            id: "cw-3",
            title: "Folk Traditions Live",
            description: "Traditional temple music and folk dance celebrations.",
            category: "Folk & Culture",
            type: .entertainment,
            durationInSeconds: 3600, // 60m
            progress: 0.85, // 85% completed
            releaseYear: 2025,
            language: "Telugu",
            imageName: "cover_folk"
        )
    ]
    
    private let featuredItems: [ContentItem] = [
        ContentItem(
            id: "feat-1",
            title: "The Weavers of Pochampally",
            description: "An epic journey into world-famous ikat art.",
            category: "Documentary",
            type: .documentary,
            durationInSeconds: 3200,
            isFeatured: true,
            releaseYear: 2026,
            language: "Telugu",
            imageName: "cover_weaving"
        ),
        ContentItem(
            id: "feat-2",
            title: "Unsung Heroes",
            description: "Grassroots innovators changing village lives.",
            category: "Stories",
            type: .story,
            durationInSeconds: 2700,
            isFeatured: true,
            releaseYear: 2026,
            language: "Telugu",
            imageName: "hero_heritage"
        ),
        ContentItem(
            id: "feat-3",
            title: "Echoes of the River",
            description: "Exploring coastal traditions along the Godavari.",
            category: "Documentary",
            type: .documentary,
            durationInSeconds: 3100,
            isFeatured: true,
            releaseYear: 2025,
            language: "Telugu",
            imageName: "cover_folk"
        )
    ]
    
    private let voicesOfSuccessItems: [ContentItem] = [
        ContentItem(
            id: "vos-1",
            title: "Grassroots Founder Podcast",
            description: "How a local artisan built an international brand.",
            category: "Podcast",
            type: .podcast,
            durationInSeconds: 2400,
            releaseYear: 2026,
            language: "English",
            imageName: "cover_podcast"
        ),
        ContentItem(
            id: "vos-2",
            title: "Agri-Tech Pioneers",
            description: "Farming innovation and community empowerment.",
            category: "Podcast",
            type: .podcast,
            durationInSeconds: 1980,
            releaseYear: 2026,
            language: "Telugu",
            imageName: "cover_weaving"
        ),
        ContentItem(
            id: "vos-3",
            title: "Women of Enterprise",
            description: "Stories of female leadership in rural cooperatives.",
            category: "Podcast",
            type: .podcast,
            durationInSeconds: 2250,
            releaseYear: 2026,
            language: "English",
            imageName: "hero_heritage"
        )
    ]
    
    private let folkAndCultureItems: [ContentItem] = [
        ContentItem(
            id: "fac-1",
            title: "Sacred Rhythms of Telangana",
            description: "Live recordings of classical and folk percussions.",
            category: "Folk & Culture",
            type: .entertainment,
            durationInSeconds: 4200,
            releaseYear: 2025,
            language: "Telugu",
            imageName: "cover_folk"
        ),
        ContentItem(
            id: "fac-2",
            title: "Shadow Puppetry Legends",
            description: "The ancient art of Tholu Bommalata.",
            category: "Heritage",
            type: .documentary,
            durationInSeconds: 2900,
            releaseYear: 2025,
            language: "Telugu",
            imageName: "hero_heritage"
        ),
        ContentItem(
            id: "fac-3",
            title: "Harvest Songs & Tales",
            description: "Celebrations of seasonal traditions.",
            category: "Folk & Culture",
            type: .entertainment,
            durationInSeconds: 3300,
            releaseYear: 2026,
            language: "Telugu",
            imageName: "cover_weaving"
        )
    ]
    
    private let empowermentItems: [ContentItem] = [
        ContentItem(
            id: "emp-1",
            title: "Weaving Hope in Pochampally",
            description: "How artisan collectives revived traditional loom weaving and created economic independence.",
            category: "Empowerment",
            type: .story,
            durationInSeconds: 2700,
            releaseYear: 2026,
            language: "Telugu",
            imageName: "cover_weaving"
        ),
        ContentItem(
            id: "emp-2",
            title: "Solar Sisters of Rural Telangana",
            description: "Women engineers bringing clean renewable solar energy to off-grid villages.",
            category: "Empowerment",
            type: .story,
            durationInSeconds: 2400,
            releaseYear: 2026,
            language: "Telugu",
            imageName: "hero_heritage"
        )
    ]
    
    private let educationItems: [ContentItem] = [
        ContentItem(
            id: "edu-1",
            title: "Digital Entrepreneurship 101",
            description: "A comprehensive masterclass on taking traditional handicrafts to digital marketplaces.",
            category: "Education & Skills",
            type: .education,
            durationInSeconds: 3600,
            releaseYear: 2026,
            language: "English",
            imageName: "cover_podcast"
        ),
        ContentItem(
            id: "edu-2",
            title: "Sustainable Agriculture Masterclass",
            description: "Organic farming techniques and water conservation for modern smallholders.",
            category: "Education & Skills",
            type: .education,
            durationInSeconds: 3100,
            releaseYear: 2026,
            language: "Telugu",
            imageName: "cover_weaving"
        )
    ]
    
    public func fetchHeroItem() async throws -> ContentItem? {
        try await Task.sleep(nanoseconds: 150_000_000)
        return heroContent
    }
    
    public func fetchFeaturedContent() async throws -> [ContentItem] {
        try await Task.sleep(nanoseconds: 150_000_000)
        return featuredItems
    }
    
    public func fetchContinueWatching() async throws -> [ContentItem] {
        try await Task.sleep(nanoseconds: 150_000_000)
        return continueWatchingItems
    }
    
    public func fetchVoicesOfSuccess() async throws -> [ContentItem] {
        try await Task.sleep(nanoseconds: 150_000_000)
        return voicesOfSuccessItems
    }
    
    public func fetchFolkAndCulture() async throws -> [ContentItem] {
        try await Task.sleep(nanoseconds: 150_000_000)
        return folkAndCultureItems
    }
    
    public func fetchCategories() async throws -> [ContentCategory] {
        try await Task.sleep(nanoseconds: 150_000_000)
        return ContentCategory.allCategories
    }
    
    public func fetchContentByCategory(id: String) async throws -> [ContentItem] {
        try await Task.sleep(nanoseconds: 150_000_000)
        switch id {
        case "folk_culture":
            return folkAndCultureItems
        case "history_docs":
            return [heroContent] + featuredItems
        case "empowerment":
            return empowermentItems
        case "voices_success":
            return voicesOfSuccessItems
        case "podcasts":
            return voicesOfSuccessItems
        case "education_skills":
            return educationItems
        default:
            return featuredItems + folkAndCultureItems
        }
    }
    
    public func searchContent(query: String) async throws -> [ContentItem] {
        try await Task.sleep(nanoseconds: 150_000_000)
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { return [] }
        let all = [heroContent] + continueWatchingItems + featuredItems + voicesOfSuccessItems + folkAndCultureItems + empowermentItems + educationItems
        return all.filter {
            $0.title.localizedCaseInsensitiveContains(query) ||
            $0.description.localizedCaseInsensitiveContains(query)
        }
    }
}
