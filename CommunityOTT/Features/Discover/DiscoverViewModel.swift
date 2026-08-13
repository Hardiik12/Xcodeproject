//
//  DiscoverViewModel.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI
import Combine

@MainActor
public final class DiscoverViewModel: ObservableObject {
    @Published public private(set) var categories: [ContentCategory] = []
    @Published public private(set) var categoryContent: [ContentItem] = []
    @Published public var selectedCategory: ContentCategory?
    @Published public private(set) var isLoading: Bool = false
    @Published public private(set) var errorMessage: String?
    
    private let repository: ContentRepositoryProtocol
    
    public init(repository: ContentRepositoryProtocol? = nil) {
        self.repository = repository ?? MockContentRepository()
    }
    
    public func loadCategories() async {
        isLoading = true
        errorMessage = nil
        do {
            self.categories = try await repository.fetchCategories()
            self.isLoading = false
        } catch {
            self.errorMessage = error.localizedDescription
            self.isLoading = false
        }
    }
    
    public func loadContent(for category: ContentCategory) async {
        selectedCategory = category
        isLoading = true
        errorMessage = nil
        do {
            self.categoryContent = try await repository.fetchContentByCategory(id: category.id)
            self.isLoading = false
        } catch {
            self.errorMessage = error.localizedDescription
            self.isLoading = false
        }
    }
}
