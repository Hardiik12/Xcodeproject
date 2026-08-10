//
//  ProfileView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI

public struct ProfileView: View {
    @StateObject private var viewModel = ProfileViewModel()
    @ObservedObject private var langStore = LanguagePreferenceStore.shared
    
    @State private var selectedContentItem: ContentItem? = nil
    let onExploreTap: (() -> Void)?
    
    public init(onExploreTap: (() -> Void)? = nil) {
        self.onExploreTap = onExploreTap
    }
    
    public var body: some View {
        ZStack {
            AppColors.background.ignoresSafeArea()
            
            VStack(alignment: .leading, spacing: 0) {
                // Screen Header
                screenHeader
                
                // Primary Vertical ScrollView
                ScrollView {
                    VStack(spacing: AppSpacing.large) {
                        // 1. Profile Header (Avatar + Name)
                        profileHeaderCard
                        
                        // 2. Statistics Summary Bar
                        statisticsRow
                        
                        // 3. Continue Watching Rail
                        continueWatchingSection
                        
                        // 4. Main Shortcuts Section (My List, Watch History, Edit Profile, Settings)
                        mainShortcutsSection
                        
                        // 5. Support & Information Section
                        supportSection
                        
                        // 6. CommunityOTT Brand Footer
                        brandFooter
                        
                        // 7. Sign Out / Sign In Action Button
                        authActionButton
                    }
                    .padding(.bottom, 100) // Inset protection for floating bottom navigation bar
                }
            }
        }
        .sheet(isPresented: $viewModel.isShowingEditProfile) {
            EditProfileView()
        }
        .sheet(isPresented: $viewModel.isShowingWatchHistory) {
            WatchHistoryView(onExploreTap: onExploreTap)
        }
        .sheet(isPresented: $viewModel.isShowingSavedList) {
            SavedView()
        }
        .sheet(isPresented: $viewModel.isShowingSettings) {
            SettingsView(
                onSignOut: {
                    viewModel.isShowingSettings = false
                    Task {
                        await viewModel.signOut()
                    }
                },
                onClose: {
                    viewModel.isShowingSettings = false
                }
            )
        }
        .sheet(isPresented: $viewModel.isShowingAbout) {
            placeholderSheet(title: langStore.localizedString(for: "About CommunityOTT"), icon: "info.circle.fill", description: "CommunityOTT is a community-rooted platform focused on cultural preservation, local achievements, and future entrepreneurship.")
        }
        .sheet(isPresented: $viewModel.isShowingHelp) {
            placeholderSheet(title: "Help & Support", icon: "questionmark.circle.fill", description: "Need assistance? Reach out to support@communityott.org or visit communityott.org/support.")
        }
        .sheet(isPresented: $viewModel.isShowingPrivacy) {
            placeholderSheet(title: "Privacy Policy", icon: "lock.shield.fill", description: "Your privacy is our priority. Read our full privacy policy at communityott.org/privacy.")
        }
        .sheet(item: $selectedContentItem) { item in
            ContentDetailsView(item: item)
        }
        .task {
            await viewModel.loadData()
        }
        .preferredColorScheme(.dark)
    }
    
    // MARK: - Header
    private var screenHeader: some View {
        HStack {
            Text(langStore.localizedString(for: "Profile"))
                .font(AppTypography.title2)
                .foregroundStyle(AppColors.textPrimary)
            Spacer()
        }
        .padding(.horizontal, AppSpacing.medium)
        .padding(.top, AppSpacing.small)
        .padding(.bottom, AppSpacing.small)
    }
    
    // MARK: - 1. Profile Header Card
    private var profileHeaderCard: some View {
        VStack(spacing: AppSpacing.medium) {
            ZStack {
                Circle()
                    .fill(AppColors.cardSurface)
                    .frame(width: 90, height: 90)
                    .overlay(
                        Circle()
                            .stroke(AppColors.primary, lineWidth: 2.5)
                    )
                
                Image(systemName: viewModel.currentAvatar.systemIcon)
                    .font(.system(size: 44))
                    .foregroundStyle(AppColors.primary)
            }
            .accessibilityLabel("Profile avatar \(viewModel.currentAvatar.title)")
            
            VStack(spacing: AppSpacing.xxSmall) {
                if case .authenticated = viewModel.authStatus {
                    Text(viewModel.displayName)
                        .font(AppTypography.title2)
                        .foregroundStyle(AppColors.textPrimary)
                    
                    Text("Community Member")
                        .font(AppTypography.subheadline)
                        .foregroundStyle(AppColors.primary)
                } else {
                    Text("Guest User")
                        .font(AppTypography.title2)
                        .foregroundStyle(AppColors.textPrimary)
                    
                    Text("Sign in to sync progress & save list")
                        .font(AppTypography.subheadline)
                        .foregroundStyle(AppColors.textSecondary)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, AppSpacing.large)
        .background(AppColors.cardSurface)
        .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.large))
        .padding(.horizontal, AppSpacing.medium)
        .shadow(color: Color.black.opacity(0.3), radius: 6, x: 0, y: 3)
    }
    
    // MARK: - 2. Statistics Summary Bar
    private var statisticsRow: some View {
        HStack(spacing: 0) {
            statCell(value: "\(viewModel.watchedCount)", label: "Watched")
            Divider()
                .frame(height: 32)
                .overlay(Color.white.opacity(0.12))
            statCell(value: "\(viewModel.savedCount)", label: "Saved")
            Divider()
                .frame(height: 32)
                .overlay(Color.white.opacity(0.12))
            statCell(value: "\(viewModel.completedCount)", label: "Completed")
        }
        .padding(.vertical, AppSpacing.medium)
        .background(AppColors.cardSurface)
        .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
        .padding(.horizontal, AppSpacing.medium)
        .accessibilityLabel("User statistics: Watched \(viewModel.watchedCount), Saved \(viewModel.savedCount), Completed \(viewModel.completedCount)")
    }
    
    private func statCell(value: String, label: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(AppTypography.title2)
                .foregroundStyle(AppColors.primary)
            Text(label)
                .font(AppTypography.caption)
                .foregroundStyle(AppColors.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }
    
    // MARK: - 3. Continue Watching Section
    private var continueWatchingSection: some View {
        VStack(alignment: .leading, spacing: AppSpacing.small) {
            Text("CONTINUE WATCHING")
                .font(AppTypography.caption)
                .foregroundStyle(AppColors.primary)
                .padding(.horizontal, AppSpacing.medium)
            
            if viewModel.continueWatchingItems.isEmpty {
                compactEmptyState
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: AppSpacing.medium) {
                        ForEach(viewModel.continueWatchingItems) { item in
                            LandscapeCardView(item: item) {
                                selectedContentItem = item
                            }
                        }
                    }
                    .padding(.horizontal, AppSpacing.medium)
                }
            }
        }
    }
    
    private var compactEmptyState: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("Nothing here yet")
                    .font(AppTypography.headline)
                    .foregroundStyle(AppColors.textPrimary)
                Text("Start watching something from CommunityOTT.")
                    .font(AppTypography.caption)
                    .foregroundStyle(AppColors.textSecondary)
            }
            Spacer()
            Button {
                onExploreTap?()
            } label: {
                Text("Explore")
                    .font(AppTypography.subheadline)
                    .foregroundStyle(AppColors.primary)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(AppColors.primary.opacity(0.15))
                    .clipShape(Capsule())
            }
        }
        .padding(AppSpacing.medium)
        .background(AppColors.cardSurface)
        .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
        .padding(.horizontal, AppSpacing.medium)
    }
    
    // MARK: - 4. Main Shortcuts Section
    private var mainShortcutsSection: some View {
        VStack(spacing: AppSpacing.xSmall) {
            Button {
                viewModel.isShowingSavedList = true
            } label: {
                shortcutRow(title: langStore.localizedString(for: "My List"), icon: "bookmark.fill", badge: "\(viewModel.savedCount)")
            }
            .accessibilityLabel("My List shortcut")
            
            Button {
                viewModel.isShowingWatchHistory = true
            } label: {
                shortcutRow(title: "Watch History", icon: "clock.arrow.circlepath", badge: nil)
            }
            .accessibilityLabel("Watch History shortcut")
            
            Button {
                viewModel.isShowingEditProfile = true
            } label: {
                shortcutRow(title: "Edit Profile", icon: "pencil.circle.fill", badge: nil)
            }
            .accessibilityLabel("Edit Profile shortcut")
            
            Button {
                viewModel.isShowingSettings = true
            } label: {
                shortcutRow(title: langStore.localizedString(for: "Settings"), icon: "gearshape.fill", badge: nil)
            }
            .accessibilityLabel("Settings shortcut")
        }
        .padding(.horizontal, AppSpacing.medium)
    }
    
    // MARK: - 5. Support Section
    private var supportSection: some View {
        VStack(spacing: AppSpacing.xSmall) {
            Button {
                viewModel.isShowingAbout = true
            } label: {
                shortcutRow(title: langStore.localizedString(for: "About CommunityOTT"), icon: "info.circle.fill", badge: nil)
            }
            
            Button {
                viewModel.isShowingHelp = true
            } label: {
                shortcutRow(title: "Help & Support", icon: "questionmark.circle.fill", badge: nil)
            }
            
            Button {
                viewModel.isShowingPrivacy = true
            } label: {
                shortcutRow(title: "Privacy Policy", icon: "lock.shield.fill", badge: nil)
            }
        }
        .padding(.horizontal, AppSpacing.medium)
    }
    
    // MARK: - 6. Brand Footer
    private var brandFooter: some View {
        VStack(spacing: AppSpacing.xxSmall) {
            Text("COMMUNITYOTT")
                .font(.system(size: 14, weight: .bold))
                .tracking(3)
                .foregroundStyle(AppColors.primary)
            
            Text("\"Our Story. Our Stage. Our Future.\"")
                .font(AppTypography.caption)
                .foregroundStyle(AppColors.textSecondary)
                .italic()
            
            Text("A home for culture, stories, achievements and community voices.")
                .font(.system(size: 11))
                .foregroundStyle(AppColors.textMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal, AppSpacing.large)
        }
        .padding(.vertical, AppSpacing.small)
        .frame(maxWidth: .infinity)
    }
    
    // MARK: - 7. Auth Action Button
    private var authActionButton: some View {
        VStack {
            if case .authenticated = viewModel.authStatus {
                SecondaryButton(
                    title: langStore.localizedString(for: "Sign Out"),
                    iconSystemName: "rectangle.portrait.and.arrow.right"
                ) {
                    Task {
                        await viewModel.signOut()
                    }
                }
                .accessibilityLabel("Sign Out button")
            } else {
                PrimaryButton(
                    title: "Sign In / Create Account",
                    iconSystemName: "person.crop.circle.badge.plus"
                ) {
                    Task {
                        await viewModel.signInAsMember()
                    }
                }
                .accessibilityLabel("Sign In or Create Account button")
            }
        }
        .padding(.horizontal, AppSpacing.medium)
    }
    
    // MARK: - Row Helper
    private func shortcutRow(title: String, icon: String, badge: String?) -> some View {
        HStack(spacing: AppSpacing.medium) {
            Image(systemName: icon)
                .font(.system(size: 18))
                .foregroundStyle(AppColors.primary)
                .frame(width: 24)
            
            Text(title)
                .font(AppTypography.headline)
                .foregroundStyle(AppColors.textPrimary)
            
            Spacer()
            
            if let badge {
                Text(badge)
                    .font(AppTypography.caption)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(AppColors.primary.opacity(0.2))
                    .foregroundStyle(AppColors.primary)
                    .clipShape(Capsule())
            }
            
            Image(systemName: "chevron.right")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(AppColors.textSecondary)
        }
        .padding(AppSpacing.medium)
        .background(AppColors.cardSurface)
        .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
    }
    
    private func placeholderSheet(title: String, icon: String, description: String) -> some View {
        ZStack {
            AppColors.background.ignoresSafeArea()
            
            VStack(spacing: AppSpacing.large) {
                Image(systemName: icon)
                    .font(.system(size: 60))
                    .foregroundStyle(AppColors.primary)
                    .padding(.top, AppSpacing.xxLarge)
                
                Text(title)
                    .font(AppTypography.title2)
                    .foregroundStyle(AppColors.textPrimary)
                
                Text(description)
                    .font(AppTypography.body)
                    .foregroundStyle(AppColors.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, AppSpacing.large)
                
                Spacer()
            }
        }
        .presentationDetents([.medium])
        .preferredColorScheme(.dark)
    }
}
