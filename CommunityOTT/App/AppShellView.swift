//
//  AppShellView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct AppShellView: View {
    @State private var selectedTab: TabItem = .home
    @ObservedObject private var langStore = LanguagePreferenceStore.shared
    let onSignOut: (() -> Void)?
    
    public init(onSignOut: (() -> Void)? = nil) {
        self.onSignOut = onSignOut
    }
    
    public var body: some View {
        ZStack(alignment: .bottom) {
            AppColors.background.ignoresSafeArea()
            
            // Active Tab Content Container (Full Width & Height)
            ZStack(alignment: .top) {
                switch selectedTab {
                case .home:
                    HomeView()
                case .discover:
                    DiscoverView()
                case .search:
                    SearchView()
                case .saved:
                    SavedView {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            selectedTab = .discover
                        }
                    }
                case .profile:
                    ProfileView {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            selectedTab = .discover
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            
            // Floating Bottom Tab Bar Overlay
            customTabBar
        }
        .preferredColorScheme(.dark)
        .ignoresSafeArea(.keyboard, edges: .bottom)
    }
    
    private var customTabBar: some View {
        HStack(spacing: 0) {
            ForEach(TabItem.allCases) { tab in
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        selectedTab = tab
                    }
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: tab.iconName)
                            .font(.system(size: 20, weight: selectedTab == tab ? .bold : .regular))
                            .foregroundStyle(selectedTab == tab ? AppColors.primary : Color.white.opacity(0.5))
                        
                        Text(langStore.localizedString(for: tab.title))
                            .font(.system(size: 10, weight: selectedTab == tab ? .semibold : .regular))
                            .foregroundStyle(selectedTab == tab ? AppColors.primary : Color.white.opacity(0.5))
                    }
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(tab.title) tab")
            }
        }
        .padding(.horizontal, AppSpacing.small)
        .padding(.vertical, AppSpacing.xSmall + 2)
        .background(
            ZStack {
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(AppColors.cardSurface.opacity(0.92))
                
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .stroke(Color.white.opacity(0.12), lineWidth: 1)
            }
        )
        .shadow(color: Color.black.opacity(0.5), radius: 12, x: 0, y: 6)
        .padding(.horizontal, AppSpacing.medium)
        .padding(.bottom, AppSpacing.xSmall) // Floating slightly above bottom edge
        .background(Color.clear)
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
