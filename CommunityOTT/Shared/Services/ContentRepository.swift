//
//  ContentRepository.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import Foundation

public protocol ContentRepositoryProtocol: Sendable {
    func fetchHeroItem() async throws -> ContentItem?
    func fetchFeaturedContent() async throws -> [ContentItem]
    func fetchContinueWatching() async throws -> [ContentItem]
    func fetchVoicesOfSuccess() async throws -> [ContentItem]
    func fetchFolkAndCulture() async throws -> [ContentItem]
    func fetchCategories() async throws -> [ContentCategory]
    func fetchContentByCategory(id: String) async throws -> [ContentItem]
    func fetchContentByIDs(ids: Set<String>) async throws -> [ContentItem]
    func searchContent(query: String) async throws -> [ContentItem]
    func getAllContentItems() async throws -> [ContentItem]
    func getContentItem(id: String) async throws -> ContentItem?
}
