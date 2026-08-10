//
//  SettingsView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI

public struct SettingsView: View {
    @ObservedObject private var langStore = LanguagePreferenceStore.shared
    @StateObject private var settingsStore = UserSettingsStore.shared
    @State private var isShowingLanguagePicker: Bool = false
    @State private var isShowingAboutSheet: Bool = false
    
    let onSignOut: () -> Void
    let onClose: () -> Void
    
    public init(onSignOut: @escaping () -> Void, onClose: @escaping () -> Void) {
        self.onSignOut = onSignOut
        self.onClose = onClose
    }
    
    public var body: some View {
        NavigationStack {
            ZStack {
                AppColors.background.ignoresSafeArea()
                
                List {
                    // MARK: - ACCOUNT SECTION
                    Section(header: sectionHeader("ACCOUNT")) {
                        HStack {
                            Image(systemName: "person.circle")
                                .foregroundStyle(AppColors.primary)
                            Text(langStore.localizedString(for: "Profile"))
                                .foregroundStyle(AppColors.textPrimary)
                        }
                        .listRowBackground(AppColors.cardSurface)
                    }
                    
                    // MARK: - PREFERENCES SECTION
                    Section(header: sectionHeader("PREFERENCES")) {
                        // Language Picker Row
                        Button {
                            isShowingLanguagePicker = true
                        } label: {
                            HStack {
                                Image(systemName: "globe")
                                    .foregroundStyle(AppColors.primary)
                                Text(langStore.localizedString(for: "Language"))
                                    .foregroundStyle(AppColors.textPrimary)
                                Spacer()
                                Text(langStore.currentLanguage.displayName)
                                    .foregroundStyle(AppColors.primary)
                                    .font(AppTypography.body)
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 14))
                                    .foregroundStyle(AppColors.textSecondary)
                            }
                        }
                        .listRowBackground(AppColors.cardSurface)
                        .accessibilityLabel("Language selector, current language \(langStore.currentLanguage.displayName)")
                        
                        // Notifications Toggle
                        Toggle(isOn: $settingsStore.notificationsEnabled) {
                            HStack {
                                Image(systemName: "bell.fill")
                                    .foregroundStyle(AppColors.primary)
                                Text(langStore.localizedString(for: "Notifications"))
                                    .foregroundStyle(AppColors.textPrimary)
                            }
                        }
                        .tint(AppColors.primary)
                        .listRowBackground(AppColors.cardSurface)
                        .accessibilityLabel("Notifications preference toggle")
                    }
                    
                    // MARK: - PLAYBACK SECTION
                    Section(header: sectionHeader("PLAYBACK")) {
                        // Autoplay Toggle
                        Toggle(isOn: $settingsStore.autoplayEnabled) {
                            HStack {
                                Image(systemName: "play.circle.fill")
                                    .foregroundStyle(AppColors.primary)
                                Text("Autoplay")
                                    .foregroundStyle(AppColors.textPrimary)
                            }
                        }
                        .tint(AppColors.primary)
                        .listRowBackground(AppColors.cardSurface)
                        .accessibilityLabel("Autoplay next video toggle")
                        
                        // Video Quality Picker
                        Picker(selection: $settingsStore.videoQuality) {
                            ForEach(VideoQuality.allCases) { quality in
                                Text(quality.displayName).tag(quality)
                            }
                        } label: {
                            HStack {
                                Image(systemName: "gearshape.fill")
                                    .foregroundStyle(AppColors.primary)
                                Text("Video Quality")
                                    .foregroundStyle(AppColors.textPrimary)
                            }
                        }
                        .pickerStyle(.menu)
                        .tint(AppColors.primary)
                        .listRowBackground(AppColors.cardSurface)
                        .accessibilityLabel("Video Quality selector")
                    }
                    
                    // MARK: - ABOUT SECTION
                    Section(header: sectionHeader("ABOUT")) {
                        Button {
                            isShowingAboutSheet = true
                        } label: {
                            HStack {
                                Image(systemName: "info.circle.fill")
                                    .foregroundStyle(AppColors.primary)
                                Text(langStore.localizedString(for: "About CommunityOTT"))
                                    .foregroundStyle(AppColors.textPrimary)
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 14))
                                    .foregroundStyle(AppColors.textSecondary)
                            }
                        }
                        .listRowBackground(AppColors.cardSurface)
                        
                        HStack {
                            Text("App Version")
                                .foregroundStyle(AppColors.textPrimary)
                            Spacer()
                            Text("1.0.0 (1)")
                                .foregroundStyle(AppColors.textSecondary)
                        }
                        .listRowBackground(AppColors.cardSurface)
                    }
                    
                    // MARK: - SIGN OUT SECTION
                    Section {
                        Button(action: onSignOut) {
                            HStack {
                                Spacer()
                                Image(systemName: "rectangle.portrait.and.arrow.right")
                                Text(langStore.localizedString(for: "Sign Out"))
                                    .font(AppTypography.headline)
                                Spacer()
                            }
                            .foregroundStyle(AppColors.error)
                        }
                        .listRowBackground(AppColors.cardSurface)
                        .accessibilityLabel("Sign Out button")
                    }
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle(langStore.localizedString(for: "Settings"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onClose) {
                        Image(systemName: "xmark")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(AppColors.textPrimary)
                    }
                    .accessibilityLabel("Close Settings")
                }
            }
            .confirmationDialog("Select App Language", isPresented: $isShowingLanguagePicker, titleVisibility: .visible) {
                ForEach(AppLanguage.allCases) { lang in
                    Button(lang.displayName) {
                        langStore.setLanguage(lang)
                    }
                }
            }
            .sheet(isPresented: $isShowingAboutSheet) {
                aboutSheet
            }
        }
        .preferredColorScheme(.dark)
    }
    
    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(AppTypography.caption)
            .foregroundStyle(AppColors.primary)
    }
    
    private var aboutSheet: some View {
        ZStack {
            AppColors.background.ignoresSafeArea()
            
            VStack(spacing: AppSpacing.large) {
                Image(systemName: "play.tv.fill")
                    .font(.system(size: 64))
                    .foregroundStyle(AppColors.primary)
                    .padding(.top, AppSpacing.xxLarge)
                
                Text("CommunityOTT")
                    .font(AppTypography.title1)
                    .foregroundStyle(AppColors.textPrimary)
                
                Text("\"Our Story, Our Stage, Our Future\"")
                    .font(AppTypography.subheadline)
                    .foregroundStyle(AppColors.primary)
                    .italic()
                
                Text("CommunityOTT is a dedicated platform focused on cultural preservation, local storytelling, grassroots achievements, and empowerment podcasts across India.")
                    .font(AppTypography.body)
                    .foregroundStyle(AppColors.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, AppSpacing.large)
                
                Spacer()
                
                Text("Version 1.0.0 (Build 1)")
                    .font(AppTypography.caption)
                    .foregroundStyle(AppColors.textMuted)
                    .padding(.bottom, AppSpacing.large)
            }
        }
        .presentationDetents([.medium])
        .preferredColorScheme(.dark)
    }
}
