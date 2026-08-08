//
//  HomeView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct HomeView: View {
    @StateObject private var viewModel = HomeViewModel()
    @State private var selectedItem: ContentItem?
    
    public init() {}
    
    public var body: some View {
        NavigationStack {
            ZStack {
                AppColors.background.ignoresSafeArea()
                
                if viewModel.isLoading {
                    LoadingView(message: "Loading CommunityOTT Home...")
                } else if let errorMsg = viewModel.errorMessage {
                    ErrorStateView(message: errorMsg) {
                        Task {
                            await viewModel.loadHomeData()
                        }
                    }
                } else {
                    ScrollView(.vertical, showsIndicators: false) {
                        VStack(spacing: AppSpacing.large) {
                            // Cinematic Hero Banner
                            if let hero = viewModel.heroItem {
                                HeroBannerView(
                                    item: hero,
                                    onWatch: {
                                        selectedItem = hero
                                    },
                                    onMyList: {}
                                )
                                .padding(.top, AppSpacing.xSmall)
                            }
                            
                            // Rail 1: Continue Watching
                            if !viewModel.continueWatching.isEmpty {
                                ContentRailView(
                                    title: "Continue Watching",
                                    subtitle: "Pick up where you left off",
                                    items: viewModel.continueWatching,
                                    variant: .continueWatching,
                                    onSelect: { item in
                                        selectedItem = item
                                    }
                                )
                            }
                            
                            // Rail 2: Featured (Poster Cards)
                            ContentRailView(
                                title: "Featured",
                                subtitle: "Curated cultural releases",
                                items: viewModel.featuredItems,
                                variant: .poster,
                                onSelect: { item in
                                    selectedItem = item
                                }
                            )
                            
                            // Rail 3: Voices of Success (Square Podcast Cards)
                            ContentRailView(
                                title: "Voices of Success",
                                subtitle: "Podcasts & Community Leaders",
                                items: viewModel.voicesOfSuccess,
                                variant: .podcast,
                                onSelect: { item in
                                    selectedItem = item
                                }
                            )
                            
                            // Rail 4: Folk & Culture (Landscape Cards)
                            ContentRailView(
                                title: "Folk & Culture",
                                subtitle: "Documentaries & Heritage Traditions",
                                items: viewModel.folkAndCulture,
                                variant: .landscape,
                                onSelect: { item in
                                    selectedItem = item
                                }
                            )
                        }
                        .padding(.bottom, AppSpacing.xxLarge + 32) // Safe padding to prevent bottom tab occlusion
                    }
                }
            }
            .navigationTitle("CommunityOTT")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        // Notifications action
                    } label: {
                        Image(systemName: "bell.fill")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(AppColors.primary)
                    }
                    .accessibilityLabel("Notifications")
                }
            }
            .task {
                await viewModel.loadHomeData()
            }
            .sheet(item: $selectedItem) { item in
                ContentDetailSheet(item: item)
            }
        }
    }
}

private struct ContentDetailSheet: View {
    let item: ContentItem
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationStack {
            ZStack {
                AppColors.background.ignoresSafeArea()
                
                VStack(spacing: AppSpacing.large) {
                    Image(systemName: "play.circle.fill")
                        .font(.system(size: 64))
                        .foregroundStyle(AppColors.primary)
                    
                    Text(item.title)
                        .font(AppTypography.title1)
                        .foregroundStyle(AppColors.textPrimary)
                    
                    Text(item.subtitleMetadata)
                        .font(AppTypography.subheadline)
                        .foregroundStyle(AppColors.textSecondary)
                    
                    Text(item.description)
                        .font(AppTypography.body)
                        .foregroundStyle(AppColors.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, AppSpacing.large)
                    
                    PrimaryButton(title: "Start Streaming", iconSystemName: "play.fill") {
                        dismiss()
                    }
                    .frame(maxWidth: 260)
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") {
                        dismiss()
                    }
                    .foregroundStyle(AppColors.primary)
                }
            }
        }
    }
}

#Preview {
    HomeView()
}
