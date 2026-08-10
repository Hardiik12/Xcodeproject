//
//  HomeView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct HomeView: View {
    @StateObject private var viewModel = HomeViewModel()
    @StateObject private var notificationStore = NotificationStore.shared
    @ObservedObject private var langStore = LanguagePreferenceStore.shared
    @State private var selectedItem: ContentItem?
    @State private var isShowingNotifications = false
    
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
                            title: langStore.localizedString(for: "Featured Stories"),
                            subtitle: "Curated cultural releases",
                            items: viewModel.featuredItems,
                            variant: .poster,
                            onSelect: { item in
                                selectedItem = item
                            }
                        )
                        
                        // Rail 3: Voices of Success (Square Podcast Cards)
                        ContentRailView(
                            title: langStore.localizedString(for: "Voices of Success"),
                            subtitle: "Podcasts & Community Leaders",
                            items: viewModel.voicesOfSuccess,
                            variant: .podcast,
                            onSelect: { item in
                                selectedItem = item
                            }
                        )
                        
                        // Rail 4: Folk & Culture (Landscape Cards)
                        ContentRailView(
                            title: langStore.localizedString(for: "Folk & Cultural Arts"),
                            subtitle: "Documentaries & Heritage Traditions",
                            items: viewModel.folkAndCulture,
                            variant: .landscape,
                            onSelect: { item in
                                selectedItem = item
                            }
                        )
                    }
                    .padding(.top, AppSpacing.xxSmall)
                    .padding(.bottom, 110) // Ensure final content cards scroll cleanly above floating tab bar
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
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
                isShowingNotifications = true
            } label: {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: "bell.fill")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(AppColors.primary)
                        .padding(8)
                        .background(AppColors.cardSurface)
                        .clipShape(Circle())
                    
                    if notificationStore.unreadCount > 0 {
                        Circle()
                            .fill(AppColors.primary)
                            .frame(width: 8, height: 8)
                            .offset(x: -1, y: 1)
                    }
                }
            }
            .accessibilityLabel("Notifications, \(notificationStore.unreadCount) unread")
        }
        .padding(.horizontal, AppSpacing.medium)
        .padding(.top, AppSpacing.xxSmall)
        .padding(.bottom, AppSpacing.xxSmall)
        .sheet(isPresented: $isShowingNotifications) {
            NotificationsView()
        }
    }
}

#Preview {
    HomeView()
}
