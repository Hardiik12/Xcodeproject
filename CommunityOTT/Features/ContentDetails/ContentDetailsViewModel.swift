//
//  ContentDetailsViewModel.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI
import Combine

@MainActor
public final class ContentDetailsViewModel: ObservableObject {
    @Published public private(set) var mediaStream: MediaStream?
    @Published public private(set) var relatedItems: [ContentItem] = []
    @Published public var isPlayingVideo: Bool = false
    @Published public var isSavedToList: Bool = false
    @Published public var isLoading: Bool = false
    @Published public var errorMessage: String?
    
    public let item: ContentItem
    private let mediaRepository: MediaRepositoryProtocol
    private let contentRepository: ContentRepositoryProtocol
    
    private var cancellables = Set<AnyCancellable>()
    
    public init(
        item: ContentItem,
        mediaRepository: MediaRepositoryProtocol? = nil,
        contentRepository: ContentRepositoryProtocol? = nil
    ) {
        self.item = item
        self.mediaRepository = mediaRepository ?? MockMediaRepository()
        self.contentRepository = contentRepository ?? MockContentRepository()
        
        // Subscribe to SavedContentStore for live updates
        SavedContentStore.shared.$savedIDs
            .receive(on: DispatchQueue.main)
            .sink { [weak self] savedSet in
                guard let self = self else { return }
                self.isSavedToList = savedSet.contains(self.item.id)
            }
            .store(in: &cancellables)
    }
    
    public func loadDetails() async {
        isLoading = true
        errorMessage = nil
        
        do {
            async let streamFetch = mediaRepository.fetchMediaStream(for: item.id)
            async let relatedFetch = contentRepository.fetchFeaturedContent()
            
            let (stream, related) = try await (streamFetch, relatedFetch)
            self.mediaStream = stream
            self.relatedItems = related.filter { $0.id != item.id }
            self.isLoading = false
        } catch {
            self.errorMessage = "Failed to load content details."
            self.isLoading = false
        }
    }
    
    public func toggleMyList() {
        SavedContentStore.shared.toggleSave(contentID: item.id)
    }
}
