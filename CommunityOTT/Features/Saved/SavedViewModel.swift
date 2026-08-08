//
//  SavedViewModel.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI
import Combine

@MainActor
public final class SavedViewModel: ObservableObject {
    @Published public private(set) var savedItems: [ContentItem] = []
    @Published public private(set) var isLoading: Bool = false
    @Published public private(set) var errorMessage: String?
    
    private let repository: ContentRepositoryProtocol
    private var cancellables = Set<AnyCancellable>()
    
    public init(repository: ContentRepositoryProtocol = MockContentRepository()) {
        self.repository = repository
        setupSavedStoreSubscription()
    }
    
    private func setupSavedStoreSubscription() {
        SavedContentStore.shared.$savedIDs
            .receive(on: DispatchQueue.main)
            .sink { [weak self] ids in
                Task { [weak self] in
                    await self?.fetchSavedItems(for: ids)
                }
            }
            .store(in: &cancellables)
    }
    
    public func loadSavedContent() async {
        await fetchSavedItems(for: SavedContentStore.shared.savedIDs)
    }
    
    public func removeItem(contentID: String) {
        SavedContentStore.shared.remove(contentID: contentID)
    }
    
    private func fetchSavedItems(for ids: Set<String>) async {
        guard !ids.isEmpty else {
            self.savedItems = []
            self.isLoading = false
            return
        }
        
        self.isLoading = true
        self.errorMessage = nil
        
        do {
            let items = try await repository.fetchContentByIDs(ids: ids)
            self.savedItems = items
            self.isLoading = false
        } catch {
            self.errorMessage = "Failed to load saved items."
            self.isLoading = false
        }
    }
}
