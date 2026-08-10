//
//  RemoteContentRepository.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import Foundation

public final class RemoteContentRepository: ContentRepositoryProtocol, @unchecked Sendable {
    private let apiClient: APIClientProtocol
    private let fallbackMockRepository: ContentRepositoryProtocol
    
    public init(
        apiClient: APIClientProtocol = URLSessionAPIClient(),
        fallbackMockRepository: ContentRepositoryProtocol = MockContentRepository()
    ) {
        self.apiClient = apiClient
        self.fallbackMockRepository = fallbackMockRepository
    }
    
    public func fetchHeroItem() async throws -> ContentItem? {
        if AppConfiguration.shared.isMockDataEnabled {
            return try await fallbackMockRepository.fetchHeroItem()
        }
        do {
            return try await apiClient.request(ContentEndpoint.homeSections)
        } catch {
            return try await fallbackMockRepository.fetchHeroItem()
        }
    }
    
    public func fetchFeaturedContent() async throws -> [ContentItem] {
        if AppConfiguration.shared.isMockDataEnabled {
            return try await fallbackMockRepository.fetchFeaturedContent()
        }
        do {
            return try await apiClient.request(ContentEndpoint.homeSections)
        } catch {
            return try await fallbackMockRepository.fetchFeaturedContent()
        }
    }
    
    public func fetchContinueWatching() async throws -> [ContentItem] {
        if AppConfiguration.shared.isMockDataEnabled {
            return try await fallbackMockRepository.fetchContinueWatching()
        }
        do {
            return try await apiClient.request(ContentEndpoint.homeSections)
        } catch {
            return try await fallbackMockRepository.fetchContinueWatching()
        }
    }
    
    public func fetchVoicesOfSuccess() async throws -> [ContentItem] {
        if AppConfiguration.shared.isMockDataEnabled {
            return try await fallbackMockRepository.fetchVoicesOfSuccess()
        }
        do {
            return try await apiClient.request(ContentEndpoint.homeSections)
        } catch {
            return try await fallbackMockRepository.fetchVoicesOfSuccess()
        }
    }
    
    public func fetchFolkAndCulture() async throws -> [ContentItem] {
        if AppConfiguration.shared.isMockDataEnabled {
            return try await fallbackMockRepository.fetchFolkAndCulture()
        }
        do {
            return try await apiClient.request(ContentEndpoint.homeSections)
        } catch {
            return try await fallbackMockRepository.fetchFolkAndCulture()
        }
    }
    
    public func fetchCategories() async throws -> [ContentCategory] {
        if AppConfiguration.shared.isMockDataEnabled {
            return try await fallbackMockRepository.fetchCategories()
        }
        do {
            return try await apiClient.request(CategoryEndpoint.listCategories)
        } catch {
            return try await fallbackMockRepository.fetchCategories()
        }
    }
    
    public func fetchContentByCategory(id: String) async throws -> [ContentItem] {
        if AppConfiguration.shared.isMockDataEnabled {
            return try await fallbackMockRepository.fetchContentByCategory(id: id)
        }
        do {
            return try await apiClient.request(ContentEndpoint.contentByCategory(categoryID: id))
        } catch {
            return try await fallbackMockRepository.fetchContentByCategory(id: id)
        }
    }
    
    public func fetchContentByIDs(ids: Set<String>) async throws -> [ContentItem] {
        if AppConfiguration.shared.isMockDataEnabled {
            return try await fallbackMockRepository.fetchContentByIDs(ids: ids)
        }
        do {
            return try await apiClient.request(ContentEndpoint.contentByIDs(ids: Array(ids)))
        } catch {
            return try await fallbackMockRepository.fetchContentByIDs(ids: ids)
        }
    }
    
    public func searchContent(query: String) async throws -> [ContentItem] {
        if AppConfiguration.shared.isMockDataEnabled {
            return try await fallbackMockRepository.searchContent(query: query)
        }
        do {
            return try await apiClient.request(SearchEndpoint.search(query: query))
        } catch {
            return try await fallbackMockRepository.searchContent(query: query)
        }
    }
    
    public func getAllContentItems() async throws -> [ContentItem] {
        return try await fallbackMockRepository.getAllContentItems()
    }
    
    public func getContentItem(id: String) async throws -> ContentItem? {
        return try await fallbackMockRepository.getContentItem(id: id)
    }
}
