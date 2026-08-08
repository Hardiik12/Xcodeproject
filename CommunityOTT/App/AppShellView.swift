//
//  AppShellView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct AppShellView: View {
    @State private var selectedTab: TabItem = .home
    let onSignOut: (() -> Void)?
    
    public init(onSignOut: (() -> Void)? = nil) {
        self.onSignOut = onSignOut
    }
    
    public var body: some View {
        TabView(selection: $selectedTab) {
            HomeView()
                .tabItem {
                    Label(TabItem.home.title, systemImage: TabItem.home.iconName)
                }
                .tag(TabItem.home)
            
            placeholderTab(for: .discover)
                .tabItem {
                    Label(TabItem.discover.title, systemImage: TabItem.discover.iconName)
                }
                .tag(TabItem.discover)
            
            placeholderTab(for: .search)
                .tabItem {
                    Label(TabItem.search.title, systemImage: TabItem.search.iconName)
                }
                .tag(TabItem.search)
            
            placeholderTab(for: .saved)
                .tabItem {
                    Label(TabItem.saved.title, systemImage: TabItem.saved.iconName)
                }
                .tag(TabItem.saved)
            
            profileTab()
                .tabItem {
                    Label(TabItem.profile.title, systemImage: TabItem.profile.iconName)
                }
                .tag(TabItem.profile)
        }
        .tint(AppColors.primary)
        .preferredColorScheme(.dark)
    }
    
    @ViewBuilder
    private func placeholderTab(for tab: TabItem) -> some View {
        NavigationStack {
            ZStack {
                AppColors.background.ignoresSafeArea()
                
                VStack(spacing: AppSpacing.medium) {
                    Image(systemName: tab.iconName)
                        .font(.system(size: 56))
                        .foregroundStyle(AppColors.primary)
                    
                    Text(tab.title)
                        .font(AppTypography.title1)
                        .foregroundStyle(AppColors.textPrimary)
                    
                    Text("CommunityOTT \(tab.title) module initialized.")
                        .font(AppTypography.body)
                        .foregroundStyle(AppColors.textSecondary)
                }
            }
            .navigationTitle(tab.title)
        }
    }
    
    @ViewBuilder
    private func profileTab() -> some View {
        NavigationStack {
            ZStack {
                AppColors.background.ignoresSafeArea()
                
                VStack(spacing: AppSpacing.large) {
                    Image(systemName: TabItem.profile.iconName)
                        .font(.system(size: 56))
                        .foregroundStyle(AppColors.primary)
                    
                    Text("User Profile")
                        .font(AppTypography.title1)
                        .foregroundStyle(AppColors.textPrimary)
                    
                    if let onSignOut {
                        SecondaryButton(
                            title: "Sign Out",
                            iconSystemName: "rectangle.portrait.and.arrow.right",
                            action: onSignOut
                        )
                        .frame(maxWidth: 240)
                    }
                }
            }
            .navigationTitle("Profile")
        }
    }
}

#Preview {
    AppShellView()
}
