//
//  DiscoverView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct DiscoverView: View {
    @StateObject private var viewModel = DiscoverViewModel()
    @ObservedObject private var langStore = LanguagePreferenceStore.shared
    @State private var activeCategory: ContentCategory?
    @State private var selectedContentItem: ContentItem?
    
    public init() {}
    
    public var body: some View {
        ZStack {
            AppColors.background.ignoresSafeArea()
            
            VStack(alignment: .leading, spacing: 0) {
                // Header Area
                VStack(alignment: .leading, spacing: AppSpacing.xxSmall) {
                    Text(langStore.localizedString(for: "Discover"))
                        .font(AppTypography.title2)
                        .foregroundStyle(AppColors.textPrimary)
                    
                    Text("Explore stories, traditions, achievements and voices from our community.")
                        .font(AppTypography.subheadline)
                        .foregroundStyle(AppColors.textSecondary)
                }
                .padding(.horizontal, AppSpacing.medium)
                .padding(.top, AppSpacing.small)
                .padding(.bottom, AppSpacing.medium)
                
                ScrollView {
                    VStack(alignment: .leading, spacing: AppSpacing.large) {
                        if viewModel.isLoading {
                            LoadingView(message: "Loading categories...")
                                .padding(.top, AppSpacing.xxLarge)
                        } else if let errorMsg = viewModel.errorMessage {
                            ErrorStateView(message: errorMsg) {
                                Task {
                                    await viewModel.loadCategories()
                                }
                            }
                            .padding(.top, AppSpacing.xxLarge)
                        } else {
                            CategoryGridView(categories: viewModel.categories) { category in
                                Task {
                                    await viewModel.loadContent(for: category)
                                    activeCategory = category
                                }
                            }
                            .padding(.horizontal, AppSpacing.medium)
                        }
                    }
                    .padding(.bottom, 100) // Bottom inset for floating navigation bar
                }
            }
        }
        .task {
            if viewModel.categories.isEmpty {
                await viewModel.loadCategories()
            }
        }
        .fullScreenCover(item: $activeCategory) { category in
            CategoryContentView(
                category: category,
                items: viewModel.categoryContent,
                onBack: {
                    activeCategory = nil
                },
                onSelectItem: { item in
                    selectedContentItem = item
                }
            )
            .sheet(item: $selectedContentItem) { item in
                ContentDetailsView(item: item)
            }
        }
        .preferredColorScheme(.dark)
    }
}
