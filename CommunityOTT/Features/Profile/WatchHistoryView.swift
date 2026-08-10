//
//  WatchHistoryView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI

public struct WatchHistoryView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var progressStore = PlaybackProgressStore.shared
    @State private var items: [ContentItem] = []
    @State private var selectedItem: ContentItem? = nil
    @State private var isLoading: Bool = true
    
    let repository: ContentRepositoryProtocol
    let onExploreTap: (() -> Void)?
    
    public init(
        repository: ContentRepositoryProtocol = MockContentRepository(),
        onExploreTap: (() -> Void)? = nil
    ) {
        self.repository = repository
        self.onExploreTap = onExploreTap
    }
    
    public var body: some View {
        NavigationStack {
            ZStack {
                AppColors.background.ignoresSafeArea()
                
                if isLoading {
                    ProgressView()
                        .tint(AppColors.primary)
                } else if items.isEmpty {
                    emptyStateView
                } else {
                    ScrollView {
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: AppSpacing.medium), GridItem(.flexible(), spacing: AppSpacing.medium)], spacing: AppSpacing.medium) {
                            ForEach(items) { item in
                                PosterCardView(item: item) {
                                    selectedItem = item
                                }
                            }
                        }
                        .padding(AppSpacing.medium)
                        .padding(.bottom, AppSpacing.xxLarge)
                    }
                }
            }
            .navigationTitle("Watch History")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Close") {
                        dismiss()
                    }
                    .foregroundStyle(AppColors.primary)
                }
            }
            .sheet(item: $selectedItem) { item in
                ContentDetailsView(item: item)
            }
            .task {
                await loadHistory()
            }
        }
        .preferredColorScheme(.dark)
    }
    
    private var emptyStateView: some View {
        VStack(spacing: AppSpacing.medium) {
            Image(systemName: "clock.arrow.circlepath")
                .font(.system(size: 64))
                .foregroundStyle(AppColors.primary.opacity(0.8))
            
            Text("No watch history yet")
                .font(AppTypography.title2)
                .foregroundStyle(AppColors.textPrimary)
            
            Text("Start exploring CommunityOTT stories and documentaries.")
                .font(AppTypography.body)
                .foregroundStyle(AppColors.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, AppSpacing.large)
            
            PrimaryButton(
                title: "Explore",
                iconSystemName: "safari.fill"
            ) {
                dismiss()
                onExploreTap?()
            }
            .frame(maxWidth: 200)
            .padding(.top, AppSpacing.small)
        }
        .padding(AppSpacing.large)
    }
    
    private func loadHistory() async {
        let records = progressStore.savedRecords
        let watchedIDs = Set(records.keys)
        
        do {
            if watchedIDs.isEmpty {
                // If user hasn't played local videos yet, load sample continue watching items
                let sample = try await repository.fetchContinueWatching()
                self.items = sample
            } else {
                let fetched = try await repository.fetchContentByIDs(ids: watchedIDs)
                self.items = fetched
            }
        } catch {
            self.items = []
        }
        isLoading = false
    }
}
