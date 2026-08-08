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
        ZStack(alignment: .top) {
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
                    VStack(alignment: .leading, spacing: AppSpacing.medium) {
                        // Top Header Bar
                        topHeaderBar
                        
                        // Cinematic Hero Banner
                        if let hero = viewModel.heroItem {
                            HeroBannerView(
                                item: hero,
                                onWatch: {
                                    selectedItem = hero
                                },
                                onMyList: {}
                            )
                        }
                        
                        // Rail 1: Continue Watching (High Priority Initial Viewport Visibility)
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
                    .padding(.top, AppSpacing.xxSmall)
                    .padding(.bottom, AppSpacing.medium)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
        }
        .task {
            await viewModel.loadHomeData()
        }
        .sheet(item: $selectedItem) { item in
            ContentDetailsView(item: item)
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
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(AppColors.primary)
                    .padding(8)
                    .background(AppColors.cardSurface)
                    .clipShape(Circle())
            }
            .accessibilityLabel("Notifications")
        }
        .padding(.horizontal, AppSpacing.medium)
        .padding(.top, AppSpacing.xxSmall)
        .padding(.bottom, AppSpacing.xxSmall)
        .background(AppColors.background)
    }
}

#Preview {
    HomeView()
}
