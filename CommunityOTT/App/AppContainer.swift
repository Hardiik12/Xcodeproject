//
//  AppContainer.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import Foundation

public final class AppContainer: @unchecked Sendable {
    public static let shared = AppContainer()
    
    public let contentRepository: ContentRepositoryProtocol
    public let mediaRepository: MediaRepositoryProtocol
    public let authService: AuthServiceProtocol
    public let playbackProgressStore: PlaybackProgressStore
    public let savedContentStore: SavedContentStore
    public let tokenStore: TokenStoreProtocol
    public let apiClient: APIClientProtocol
    
    public init(
        configuration: AppConfiguration = AppConfiguration.shared,
        tokenStore: TokenStoreProtocol = KeychainTokenStore.shared
    ) {
        self.tokenStore = tokenStore
        self.apiClient = URLSessionAPIClient(baseURL: configuration.baseURL, tokenStore: tokenStore)
        
        if configuration.isMockDataEnabled {
            self.contentRepository = MockContentRepository()
            self.mediaRepository = MockMediaRepository()
            self.authService = MockAuthService()
        } else {
            self.contentRepository = RemoteContentRepository(apiClient: apiClient)
            self.mediaRepository = RemoteMediaRepository(apiClient: apiClient)
            self.authService = RemoteAuthService(apiClient: apiClient, tokenStore: tokenStore)
        }
        
        self.playbackProgressStore = PlaybackProgressStore.shared
        self.savedContentStore = SavedContentStore.shared
    }
}
