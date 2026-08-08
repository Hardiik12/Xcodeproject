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
                VStack(spacing: 0) {
                    // Compact Premium Home Header
                    topHeaderBar
                    
                    ScrollView(.vertical, showsIndicators: false) {
                        VStack(spacing: AppSpacing.medium) {
                            // Cinematic Hero Banner
                            if let hero = viewModel.heroItem {
                                HeroBannerView(
                                    item: hero,
                                    onWatch: {
                                        selectedItem = hero
                                    },
                                    onMyList: {}
                                )
                                .padding(.top, AppSpacing.xxSmall)
                            }
                            
                            // Rail 1: Continue Watching (High Priority Initial Viewport Visibility)
                            if !viewModel.continueWatching.isEmpty {
                                ContentRailView(
                                    title: "Continue Watching",
                                    subtitle: nil,
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
                        .padding(.bottom, 100) // Clear inset so bottom tab bar never obscures final rail
                    }
                }
            }
        }
        .task {
            await viewModel.loadHomeData()
        }
        .sheet(item: $selectedItem) { item in
            ContentDetailSheet(item: item)
        }
    }
    
    private var topHeaderBar: some View {
        HStack {
            HStack(spacing: 4) {
                Text("Community")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(AppColors.textPrimary)
                
                Text("OTT")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(AppColors.primary)
            }
            
            Spacer()
            
            Button {
                // Notification action
            } label: {
                Image(systemName: "bell.fill")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(AppColors.primary)
                    .padding(8)
                    .background(AppColors.cardSurface)
                    .clipShape(Circle())
            }
            .accessibilityLabel("Notifications")
        }
        .padding(.horizontal, AppSpacing.medium)
        .padding(.top, AppSpacing.xSmall)
        .padding(.bottom, AppSpacing.xxSmall)
        .background(AppColors.background)
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
