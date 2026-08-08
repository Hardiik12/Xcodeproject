//
//  HomeViewModel.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI
import Combine

@MainActor
public final class HomeViewModel: ObservableObject {
    @Published public private(set) var heroItem: ContentItem?
    @Published public private(set) var continueWatching: [ContentItem] = []
    @Published public private(set) var featuredItems: [ContentItem] = []
    @Published public private(set) var voicesOfSuccess: [ContentItem] = []
    @Published public private(set) var folkAndCulture: [ContentItem] = []
    
    @Published public private(set) var isLoading: Bool = false
    @Published public private(set) var errorMessage: String?
    
    private let repository: ContentRepositoryProtocol
    
    public init(repository: ContentRepositoryProtocol = MockContentRepository()) {
        self.repository = repository
    }
    
    public func loadHomeData() async {
        isLoading = true
        errorMessage = nil
        
        do {
            async let heroTask = repository.fetchHeroItem()
            async let featuredTask = repository.fetchFeaturedContent()
            async let continueTask = repository.fetchContinueWatching()
            async let voicesTask = repository.fetchVoicesOfSuccess()
            async let folkTask = repository.fetchFolkAndCulture()
            
            let (hero, featured, continueList, voices, folk) = try await (heroTask, featuredTask, continueTask, voicesTask, folkTask)
            
            self.heroItem = hero
            self.featuredItems = featured
            self.continueWatching = continueList
            self.voicesOfSuccess = voices
            self.folkAndCulture = folk
            self.isLoading = false
        } catch {
            self.errorMessage = error.localizedDescription
            self.isLoading = false
        }
    }
}
