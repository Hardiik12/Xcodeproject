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
                                .padding(.top, AppSpacing.small)
                            }
                            
                            // Rail 1: Continue Watching
                            if !viewModel.continueWatching.isEmpty {
                                ContentRailView(
                                    title: "Continue Watching",
                                    subtitle: "Pick up where you left off",
                                    items: viewModel.continueWatching,
                                    onSelect: { item in
                                        selectedItem = item
                                    }
                                )
                            }
                            
                            // Rail 2: Featured
                            ContentRailView(
                                title: "Featured",
                                subtitle: "Curated cultural releases",
                                items: viewModel.featuredItems,
                                onSelect: { item in
                                    selectedItem = item
                                }
                            )
                            
                            // Rail 3: Voices of Success
                            ContentRailView(
                                title: "Voices of Success",
                                subtitle: "Podcasts & Community Leaders",
                                items: viewModel.voicesOfSuccess,
                                onSelect: { item in
                                    selectedItem = item
                                }
                            )
                            
                            // Rail 4: Folk & Culture
                            ContentRailView(
                                title: "Folk & Culture",
                                subtitle: "Documentaries & Heritage Traditions",
                                items: viewModel.folkAndCulture,
                                onSelect: { item in
                                    selectedItem = item
                                }
                            )
                        }
                        .padding(.bottom, AppSpacing.xxLarge)
                    }
                }
            }
            .navigationTitle("CommunityOTT")
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
