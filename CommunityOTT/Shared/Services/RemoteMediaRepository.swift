//
//  RemoteMediaRepository.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import Foundation

public final class RemoteMediaRepository: MediaRepositoryProtocol, @unchecked Sendable {
    private let apiClient: APIClientProtocol
    private let fallbackMockRepository: MediaRepositoryProtocol
    
    public init(
        apiClient: APIClientProtocol = URLSessionAPIClient(),
        fallbackMockRepository: MediaRepositoryProtocol = MockMediaRepository()
    ) {
        self.apiClient = apiClient
        self.fallbackMockRepository = fallbackMockRepository
    }
    
    public func fetchMediaStream(for contentID: String) async throws -> MediaStream {
        if AppConfiguration.shared.isMockDataEnabled {
            return try await fallbackMockRepository.fetchMediaStream(for: contentID)
        }
        do {
            return try await apiClient.request(MediaEndpoint.stream(contentID: contentID))
        } catch {
            return try await fallbackMockRepository.fetchMediaStream(for: contentID)
        }
    }
}
