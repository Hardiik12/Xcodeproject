//
//  EditProfileView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI

public struct EditProfileView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var profileStore = UserProfileStore.shared
    
    @State private var nameInput: String = ""
    @State private var selectedAvatarID: String = "gold"
    @State private var isSavedSuccessfully: Bool = false
    
    public init() {}
    
    public var body: some View {
        NavigationStack {
            ZStack {
                AppColors.background.ignoresSafeArea()
                
                ScrollView {
                    VStack(spacing: AppSpacing.large) {
                        // Avatar Selection Preview
                        avatarPreviewHeader
                            .padding(.top, AppSpacing.medium)
                        
                        // Display Name Section
                        VStack(alignment: .leading, spacing: AppSpacing.xSmall) {
                            Text("DISPLAY NAME")
                                .font(AppTypography.caption)
                                .foregroundStyle(AppColors.primary)
                            
                            TextField("Enter your name", text: $nameInput)
                                .font(AppTypography.body)
                                .foregroundStyle(AppColors.textPrimary)
                                .padding(AppSpacing.medium)
                                .background(AppColors.cardSurface)
                                .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
                                .overlay(
                                    RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium)
                                        .stroke(Color.white.opacity(0.12), lineWidth: 1)
                                )
                        }
                        .padding(.horizontal, AppSpacing.medium)
                        
                        // Avatar Options Grid
                        VStack(alignment: .leading, spacing: AppSpacing.small) {
                            Text("AVATAR STYLE")
                                .font(AppTypography.caption)
                                .foregroundStyle(AppColors.primary)
                                .padding(.horizontal, AppSpacing.medium)
                            
                            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: AppSpacing.medium) {
                                ForEach(AvatarOption.defaultAvatars) { avatar in
                                    Button {
                                        withAnimation(.easeInOut(duration: 0.2)) {
                                            selectedAvatarID = avatar.id
                                        }
                                    } label: {
                                        HStack(spacing: AppSpacing.small) {
                                            Image(systemName: avatar.systemIcon)
                                                .font(.system(size: 24))
                                                .foregroundStyle(selectedAvatarID == avatar.id ? AppColors.primary : AppColors.textSecondary)
                                            
                                            Text(avatar.title)
                                                .font(AppTypography.subheadline)
                                                .foregroundStyle(selectedAvatarID == avatar.id ? AppColors.textPrimary : AppColors.textSecondary)
                                            
                                            Spacer()
                                            
                                            if selectedAvatarID == avatar.id {
                                                Image(systemName: "checkmark.circle.fill")
                                                    .foregroundStyle(AppColors.primary)
                                            }
                                        }
                                        .padding(AppSpacing.medium)
                                        .background(selectedAvatarID == avatar.id ? AppColors.cardSurface : AppColors.cardSurface.opacity(0.5))
                                        .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium)
                                                .stroke(selectedAvatarID == avatar.id ? AppColors.primary : Color.white.opacity(0.1), lineWidth: 1.5)
                                        )
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel("Select avatar style \(avatar.title)")
                                }
                            }
                            .padding(.horizontal, AppSpacing.medium)
                        }
                        
                        // Save Button
                        VStack {
                            PrimaryButton(
                                title: isSavedSuccessfully ? "Saved ✓" : "Save Changes",
                                iconSystemName: isSavedSuccessfully ? "checkmark" : "square.and.arrow.down"
                            ) {
                                saveChanges()
                            }
                            .disabled(nameInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        }
                        .padding(.horizontal, AppSpacing.medium)
                        .padding(.top, AppSpacing.medium)
                    }
                    .padding(.bottom, AppSpacing.xxLarge)
                }
            }
            .navigationTitle("Edit Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                    .foregroundStyle(AppColors.primary)
                }
            }
            .onAppear {
                nameInput = profileStore.displayName
                selectedAvatarID = profileStore.avatarID
            }
        }
        .preferredColorScheme(.dark)
    }
    
    private var avatarPreviewHeader: some View {
        let avatar = AvatarOption.defaultAvatars.first { $0.id == selectedAvatarID } ?? AvatarOption.defaultAvatars[0]
        
        return VStack(spacing: AppSpacing.small) {
            ZStack {
                Circle()
                    .fill(AppColors.cardSurface)
                    .frame(width: 100, height: 100)
                    .overlay(
                        Circle()
                            .stroke(AppColors.primary, lineWidth: 2.5)
                    )
                
                Image(systemName: avatar.systemIcon)
                    .font(.system(size: 48))
                    .foregroundStyle(AppColors.primary)
            }
            .accessibilityLabel("Selected avatar preview")
            
            Text(avatar.title)
                .font(AppTypography.caption)
                .foregroundStyle(AppColors.textSecondary)
        }
    }
    
    private func saveChanges() {
        let trimmed = nameInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        
        profileStore.updateProfile(name: trimmed, avatarID: selectedAvatarID)
        
        withAnimation {
            isSavedSuccessfully = true
        }
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
            dismiss()
        }
    }
}
