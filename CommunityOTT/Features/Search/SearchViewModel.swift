//
//  SearchViewModel.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI
import Combine

@MainActor
public final class SearchViewModel: ObservableObject {
    @Published public var searchQuery: String = ""
    @Published public var selectedType: ContentFilterType = .all
    @Published public var selectedCategory: String = "All"
    @Published public var selectedLanguage: String = "All"
    
    @Published public private(set) var searchResults: [ContentItem] = []
    @Published public private(set) var isLoading: Bool = false
    @Published public var selectedItem: ContentItem?
    
    private let repository: ContentRepositoryProtocol
    private var allItems: [ContentItem] = []
    private var cancellables = Set<AnyCancellable>()
    
    public init(repository: ContentRepositoryProtocol = MockContentRepository()) {
        self.repository = repository
        setupSearchPublisher()
    }
    
    public var activeFilterCount: Int {
        var count = 0
        if selectedType != .all { count += 1 }
        if selectedCategory != "All" { count += 1 }
        if selectedLanguage != "All" { count += 1 }
        return count
    }
    
    public var hasActiveFilters: Bool {
        activeFilterCount > 0
    }
    
    public func loadInitialData() async {
        isLoading = true
        do {
            self.allItems = try await repository.getAllContentItems()
            performSearch()
        } catch {
            self.allItems = []
            self.searchResults = []
        }
        isLoading = false
    }
    
    public func performSearch() {
        var results = allItems
        
        // 1. Text Query Filter
        let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if !query.isEmpty {
            results = results.filter { item in
                item.title.lowercased().contains(query) ||
                item.description.lowercased().contains(query) ||
                item.category.lowercased().contains(query)
            }
        }
        
        // 2. Content Type Filter
        if selectedType != .all {
            results = results.filter { item in
                switch selectedType {
                case .documentary: return item.type == .documentary
                case .podcast: return item.type == .podcast
                case .story: return item.type == .story
                case .education: return item.type == .education
                case .all: return true
                }
            }
        }
        
        // 3. Category Filter
        if selectedCategory != "All" {
            results = results.filter { item in
                item.category.lowercased() == selectedCategory.lowercased()
            }
        }
        
        // 4. Language Filter
        if selectedLanguage != "All" {
            results = results.filter { item in
                item.language.lowercased().contains(selectedLanguage.lowercased())
            }
        }
        
        withAnimation(.easeInOut(duration: 0.2)) {
            self.searchResults = results
        }
    }
    
    public func clearFilters() {
        selectedType = .all
        selectedCategory = "All"
        selectedLanguage = "All"
        performSearch()
    }
    
    public func clearSearch() {
        searchQuery = ""
        performSearch()
    }
    
    private func setupSearchPublisher() {
        $searchQuery
            .dropFirst()
            .debounce(for: .milliseconds(250), scheduler: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.performSearch()
            }
            .store(in: &cancellables)
    }
}
