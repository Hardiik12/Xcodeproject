//
//  SavedView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct SavedView: View {
    @StateObject private var viewModel = SavedViewModel()
    @ObservedObject private var langStore = LanguagePreferenceStore.shared
    @State private var selectedItem: ContentItem?
    let onExploreContent: (() -> Void)?
    
    private let columns = [
        GridItem(.flexible(), spacing: AppSpacing.medium),
        GridItem(.flexible(), spacing: AppSpacing.medium)
    ]
    
    public init(onExploreContent: (() -> Void)? = nil) {
        self.onExploreContent = onExploreContent
    }
    
    public var body: some View {
        ZStack {
            AppColors.background.ignoresSafeArea()
            
            VStack(alignment: .leading, spacing: 0) {
                // Header Area
                VStack(alignment: .leading, spacing: AppSpacing.xxSmall) {
                    Text(langStore.localizedString(for: "My List"))
                        .font(AppTypography.title2)
                        .foregroundStyle(AppColors.textPrimary)
                    
                    Text("Saved movies, documentaries, podcasts and stories to watch later.")
                        .font(AppTypography.subheadline)
                        .foregroundStyle(AppColors.textSecondary)
                }
                .padding(.horizontal, AppSpacing.medium)
                .padding(.top, AppSpacing.small)
                .padding(.bottom, AppSpacing.medium)
                
                ScrollView {
                    VStack(alignment: .leading, spacing: AppSpacing.large) {
                        if viewModel.isLoading {
                            LoadingView(message: "Loading your saved list...")
                                .padding(.top, AppSpacing.xxLarge)
                        } else if viewModel.savedItems.isEmpty {
                            // Polished Empty State
                            VStack(spacing: AppSpacing.medium) {
                                Image(systemName: "bookmark")
                                    .font(.system(size: 56))
                                    .foregroundStyle(AppColors.primary.opacity(0.8))
                                
                                Text("Your List is Empty")
                                    .font(AppTypography.title2)
                                    .foregroundStyle(AppColors.textPrimary)
                                
                                Text("Save movies, documentaries, podcasts and stories to watch later.")
                                    .font(AppTypography.body)
                                    .foregroundStyle(AppColors.textSecondary)
                                    .multilineTextAlignment(.center)
                                    .padding(.horizontal, AppSpacing.large)
                                
                                if let onExploreContent {
                                    PrimaryButton(title: "Explore Content", iconSystemName: "compass") {
                                        onExploreContent()
                                    }
                                    .frame(maxWidth: 220)
                                    .padding(.top, AppSpacing.small)
                                }
                            }
                            .padding(.top, AppSpacing.xxLarge)
                            .padding(.horizontal, AppSpacing.medium)
                        } else {
                            // Saved Items Grid
                            LazyVGrid(columns: columns, spacing: AppSpacing.medium) {
                                ForEach(viewModel.savedItems) { item in
                                    ZStack(alignment: .topTrailing) {
                                        PosterCardView(item: item) {
                                            selectedItem = item
                                        }
                                        
                                        // Quick Remove Button Overlay
                                        Button {
                                            viewModel.removeItem(contentID: item.id)
                                        } label: {
                                            Image(systemName: "trash.fill")
                                                .font(.system(size: 12, weight: .bold))
                                                .foregroundStyle(Color.white)
                                                .padding(6)
                                                .background(Color.black.opacity(0.75))
                                                .clipShape(Circle())
                                        }
                                        .padding(AppSpacing.xSmall)
                                        .accessibilityLabel("Remove \(item.title) from My List")
                                    }
                                }
                            }
                            .padding(.horizontal, AppSpacing.medium)
                        }
                    }
                    .padding(.bottom, 100) // Inset for floating navigation tab bar
                }
            }
        }
        .task {
            await viewModel.loadSavedContent()
        }
        .sheet(item: $selectedItem) { item in
            ContentDetailsView(item: item)
        }
        .preferredColorScheme(.dark)
    }
}
