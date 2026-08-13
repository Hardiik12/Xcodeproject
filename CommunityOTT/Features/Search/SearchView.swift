//
//  SearchView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI

@MainActor
public struct SearchView: View {
    @StateObject private var viewModel = SearchViewModel()
    @ObservedObject private var langStore = LanguagePreferenceStore.shared
    @State private var isShowingFilterSheet = false
    
    public init() {}
    
    public var body: some View {
        NavigationStack {
            ZStack {
                AppColors.background.ignoresSafeArea()
                
                VStack(spacing: AppSpacing.medium) {
                    // Search Bar & Filter Button Bar
                    searchHeaderBar
                        .padding(.horizontal, AppSpacing.medium)
                        .padding(.top, AppSpacing.xSmall)
                    
                    // Active Filter Badges Bar (if active)
                    if viewModel.hasActiveFilters {
                        activeFilterChipsRow
                            .padding(.horizontal, AppSpacing.medium)
                    }
                    
                    // Content Area
                    if viewModel.isLoading {
                        LoadingView(message: "Searching CommunityOTT...")
                    } else if viewModel.searchResults.isEmpty {
                        emptyResultsView
                    } else {
                        resultsGrid
                    }
                }
            }
            .navigationTitle("Search")
            .navigationBarTitleDisplayMode(.inline)
            .task {
                await viewModel.loadInitialData()
            }
            .sheet(isPresented: $isShowingFilterSheet) {
                SearchFilterSheetView(
                    selectedType: $viewModel.selectedType,
                    selectedCategory: $viewModel.selectedCategory,
                    selectedLanguage: $viewModel.selectedLanguage,
                    onClear: {
                        viewModel.clearFilters()
                    },
                    onApply: {
                        viewModel.performSearch()
                    }
                )
            }
            .sheet(item: $viewModel.selectedItem) { item in
                ContentDetailsView(item: item)
            }
        }
        .preferredColorScheme(.dark)
    }
    
    private var searchHeaderBar: some View {
        HStack(spacing: AppSpacing.small) {
            // Search Text Input Field
            HStack(spacing: AppSpacing.small) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(AppColors.primary)
                
                TextField("Search titles, categories, stories...", text: $viewModel.searchQuery)
                    .font(AppTypography.body)
                    .foregroundStyle(AppColors.textPrimary)
                    .autocorrectionDisabled()
                
                if !viewModel.searchQuery.isEmpty {
                    Button {
                        viewModel.clearSearch()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(AppColors.textMuted)
                    }
                    .accessibilityLabel("Clear search text")
                }
            }
            .padding(.horizontal, AppSpacing.medium)
            .padding(.vertical, 10)
            .background(AppColors.cardSurface)
            .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
            .overlay(
                RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium)
                    .stroke(Color.white.opacity(0.12), lineWidth: 1)
            )
            
            // Filter Button
            Button {
                isShowingFilterSheet = true
            } label: {
                ZStack(alignment: .topTrailing) {
                    HStack(spacing: 4) {
                        Image(systemName: "line.3.horizontal.decrease.circle.fill")
                            .font(.system(size: 18))
                            .foregroundStyle(viewModel.hasActiveFilters ? AppColors.primary : AppColors.textSecondary)
                    }
                    .frame(width: 44, height: 44)
                    .background(AppColors.cardSurface)
                    .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
                    .overlay(
                        RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium)
                            .stroke(viewModel.hasActiveFilters ? AppColors.primary : Color.white.opacity(0.12), lineWidth: 1)
                    )
                    
                    if viewModel.activeFilterCount > 0 {
                        Text("\(viewModel.activeFilterCount)")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(Color.black)
                            .padding(4)
                            .background(AppColors.primary)
                            .clipShape(Circle())
                            .offset(x: 4, y: -4)
                    }
                }
            }
            .accessibilityLabel("Filter search results, \(viewModel.activeFilterCount) active filters")
        }
    }
    
    private var activeFilterChipsRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: AppSpacing.xSmall) {
                if viewModel.selectedType != .all {
                    filterBadge(title: viewModel.selectedType.rawValue) {
                        viewModel.selectedType = .all
                        viewModel.performSearch()
                    }
                }
                if viewModel.selectedCategory != "All" {
                    filterBadge(title: viewModel.selectedCategory) {
                        viewModel.selectedCategory = "All"
                        viewModel.performSearch()
                    }
                }
                if viewModel.selectedLanguage != "All" {
                    filterBadge(title: viewModel.selectedLanguage) {
                        viewModel.selectedLanguage = "All"
                        viewModel.performSearch()
                    }
                }
                
                Button("Reset All") {
                    viewModel.clearFilters()
                }
                .font(AppTypography.caption)
                .foregroundStyle(AppColors.primary)
                .padding(.leading, AppSpacing.xSmall)
            }
        }
    }
    
    private func filterBadge(title: String, onRemove: @escaping () -> Void) -> some View {
        HStack(spacing: 4) {
            Text(title)
                .font(AppTypography.caption)
                .foregroundStyle(AppColors.textPrimary)
            
            Button(action: onRemove) {
                Image(systemName: "xmark")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(AppColors.textSecondary)
            }
        }
        .padding(.horizontal, AppSpacing.small)
        .padding(.vertical, 4)
        .background(AppColors.cardSurface)
        .clipShape(Capsule())
        .overlay(
            Capsule()
                .stroke(AppColors.primary.opacity(0.5), lineWidth: 1)
        )
    }
    
    private var emptyResultsView: some View {
        VStack(spacing: AppSpacing.medium) {
            Spacer()
            
            Image(systemName: "magnifyingglass.circle.fill")
                .font(.system(size: 56))
                .foregroundStyle(AppColors.primary.opacity(0.6))
            
            Text("No results found")
                .font(AppTypography.title2)
                .foregroundStyle(AppColors.textPrimary)
            
            Text("Try changing your search terms or active filters.")
                .font(AppTypography.body)
                .foregroundStyle(AppColors.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, AppSpacing.large)
            
            if viewModel.hasActiveFilters {
                SecondaryButton(
                    title: "Clear Filters",
                    iconSystemName: "arrow.counterclockwise",
                    action: {
                        viewModel.clearFilters()
                    }
                )
                .frame(maxWidth: 220)
                .padding(.top, AppSpacing.small)
            }
            
            Spacer()
        }
    }
    
    private var resultsGrid: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.flexible(), spacing: AppSpacing.medium), GridItem(.flexible(), spacing: AppSpacing.medium)], spacing: AppSpacing.medium) {
                ForEach(viewModel.searchResults) { item in
                    PosterCardView(item: item) {
                        viewModel.selectedItem = item
                    }
                }
            }
            .padding(.horizontal, AppSpacing.medium)
            .padding(.top, AppSpacing.small)
            .padding(.bottom, 100) // Ensure final search results scroll above floating bottom navigation bar
        }
    }
}
