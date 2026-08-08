//
//  MockContentRepository.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import Foundation

public final class MockContentRepository: ContentRepositoryProtocol, @unchecked Sendable {
    public init() {}
    
    private let heroContent = ContentItem(
        id: "hero-1",
        title: "Stories of Our Heritage",
        description: "Discover stories that deserve to be remembered. Explore deep cultural traditions and living legacies.",
        category: "Featured Documentary",
        type: .documentary,
        isFeatured: true,
        releaseYear: 2026,
        language: "Telugu"
    )
    
    private let mockItems: [ContentItem] = [
        ContentItem(
            id: "1",
            title: "Roots of Culture",
            description: "A rich documentary exploring local traditions, folklore, and heritage preservation.",
            category: "Documentaries",
            type: .documentary,
            isFeatured: true,
            releaseYear: 2026,
            language: "Telugu"
        ),
        ContentItem(
            id: "2",
            title: "Voices of Tomorrow",
            description: "Inspiring podcasts featuring community leaders, artisans, and innovators.",
            category: "Podcasts",
            type: .podcast,
            isFeatured: true,
            releaseYear: 2026,
            language: "English"
        ),
        ContentItem(
            id: "3",
            title: "Village Chronicles",
            description: "Heartwarming community stories showcasing grassroots empowerment.",
            category: "Stories",
            type: .story,
            isFeatured: false,
            releaseYear: 2025,
            language: "Telugu"
        ),
        ContentItem(
            id: "4",
            title: "Heritage Rhythms",
            description: "Cultural music and artistic performances recorded live.",
            category: "Entertainment",
            type: .entertainment,
            isFeatured: true,
            releaseYear: 2026,
            language: "Telugu"
        ),
        ContentItem(
            id: "5",
            title: "Craft & Skill Masterclass",
            description: "Educational modules empowering rural and urban youth.",
            category: "Education",
            type: .education,
            isFeatured: false,
            releaseYear: 2026,
            language: "English"
        ),
        ContentItem(
            id: "6",
            title: "Grassroots Innovators",
            description: "Entrepreneurs shaping local economies and sustainable development.",
            category: "Success Stories",
            type: .story,
            isFeatured: true,
            releaseYear: 2026,
            language: "Telugu"
        ),
        ContentItem(
            id: "7",
            title: "Sacred Echoes",
            description: "Ancient folk songs, oral histories, and temple celebrations.",
            category: "Folk & Culture",
            type: .documentary,
            isFeatured: true,
            releaseYear: 2025,
            language: "Telugu"
        )
    ]
    
    private let mockCategories: [ContentCategory] = [
        ContentCategory(id: "doc", title: "Documentaries", type: .documentary),
        ContentCategory(id: "pod", title: "Podcasts", type: .podcast),
        ContentCategory(id: "ent", title: "Entertainment", type: .entertainment),
        ContentCategory(id: "edu", title: "Education", type: .education),
        ContentCategory(id: "sty", title: "Community Stories", type: .story)
    ]
    
    public func fetchHeroItem() async throws -> ContentItem? {
        try await Task.sleep(nanoseconds: 150_000_000)
        return heroContent
    }
    
    public func fetchFeaturedContent() async throws -> [ContentItem] {
        try await Task.sleep(nanoseconds: 200_000_000)
        return mockItems.filter { $0.isFeatured }
    }
    
    public func fetchContinueWatching() async throws -> [ContentItem] {
        try await Task.sleep(nanoseconds: 150_000_000)
        return Array(mockItems.prefix(3))
    }
    
    public func fetchVoicesOfSuccess() async throws -> [ContentItem] {
        try await Task.sleep(nanoseconds: 200_000_000)
        return mockItems.filter { $0.type == .podcast || $0.type == .story || $0.category == "Success Stories" }
    }
    
    public func fetchFolkAndCulture() async throws -> [ContentItem] {
        try await Task.sleep(nanoseconds: 200_000_000)
        return mockItems.filter { $0.type == .documentary || $0.category == "Folk & Culture" || $0.type == .entertainment }
    }
    
    public func fetchCategories() async throws -> [ContentCategory] {
        try await Task.sleep(nanoseconds: 150_000_000)
        return mockCategories
    }
    
    public func fetchContentByCategory(id: String) async throws -> [ContentItem] {
        try await Task.sleep(nanoseconds: 200_000_000)
        return mockItems
    }
    
    public func searchContent(query: String) async throws -> [ContentItem] {
        try await Task.sleep(nanoseconds: 200_000_000)
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { return [] }
        return mockItems.filter {
            $0.title.localizedCaseInsensitiveContains(query) ||
            $0.description.localizedCaseInsensitiveContains(query)
        }
    }
}
