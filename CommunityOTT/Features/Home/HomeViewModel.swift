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
    private var baseContinueWatching: [ContentItem] = []
    private var allKnownItems: [ContentItem] = []
    private var cancellables = Set<AnyCancellable>()
    
    public init(repository: ContentRepositoryProtocol? = nil) {
        self.repository = repository ?? MockContentRepository()
        setupProgressStoreSubscription()
    }
    
    private func setupProgressStoreSubscription() {
        PlaybackProgressStore.shared.$savedRecords
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.refreshContinueWatching()
            }
            .store(in: &cancellables)
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
            self.voicesOfSuccess = voices
            self.folkAndCulture = folk
            self.baseContinueWatching = continueList
            
            // Gather all items to support adding newly played items to Continue Watching
            var itemsMap: [String: ContentItem] = [:]
            if let hero { itemsMap[hero.id] = hero }
            for item in continueList + featured + voices + folk {
                itemsMap[item.id] = item
            }
            self.allKnownItems = Array(itemsMap.values)
            
            refreshContinueWatching()
            self.isLoading = false
        } catch {
            self.errorMessage = error.localizedDescription
            self.isLoading = false
        }
    }
    
    public func refreshContinueWatching() {
        var items: [ContentItem] = []
        var processedIDs = Set<String>()
        
        // 1. Process base continue watching list
        for item in baseContinueWatching {
            processedIDs.insert(item.id)
            if PlaybackProgressStore.shared.isCompleted(for: item.id) {
                // Remove completed content (>= 95%) from Continue Watching
                continue
            } else if let localRecord = PlaybackProgressStore.shared.getRecord(for: item.id) {
                items.append(item.withProgress(localRecord.completionPercentage))
            } else {
                items.append(item)
            }
        }
        
        // 2. Include any other known content that has active local progress
        for item in allKnownItems where !processedIDs.contains(item.id) {
            if let localRecord = PlaybackProgressStore.shared.getRecord(for: item.id), !localRecord.isCompleted {
                items.append(item.withProgress(localRecord.completionPercentage))
            }
        }
        
        self.continueWatching = items
    }
}
