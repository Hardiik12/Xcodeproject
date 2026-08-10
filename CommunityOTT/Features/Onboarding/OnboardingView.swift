//
//  OnboardingView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI

public struct OnboardingView: View {
    @StateObject private var viewModel = OnboardingViewModel()
    let onComplete: () -> Void
    
    public init(onComplete: @escaping () -> Void) {
        self.onComplete = onComplete
    }
    
    public var body: some View {
        ZStack {
            AppColors.background.ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Top Navigation Bar (Back & Skip)
                topNavBar
                
                // Step Progress Indicator
                stepProgressIndicator
                    .padding(.top, AppSpacing.xSmall)
                    .padding(.bottom, AppSpacing.medium)
                
                // Active Step View Container
                ScrollView {
                    VStack {
                        switch viewModel.stepIndex {
                        case 0:
                            OnboardingPageView()
                        case 1:
                            LanguageSelectionView()
                        case 2:
                            InterestSelectionView(selectedIDs: $viewModel.selectedInterests)
                        default:
                            EmptyView()
                        }
                    }
                    .padding(.vertical, AppSpacing.medium)
                }
                
                Spacer()
                
                // Bottom Control Action Button
                bottomControls
                    .padding(.horizontal, AppSpacing.medium)
                    .padding(.bottom, AppSpacing.large)
            }
        }
        .preferredColorScheme(.dark)
    }
    
    private var topNavBar: some View {
        HStack {
            if !viewModel.isFirstStep {
                Button {
                    viewModel.previousStep()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 16, weight: .bold))
                        Text("Back")
                            .font(AppTypography.subheadline)
                    }
                    .foregroundStyle(AppColors.primary)
                }
                .accessibilityLabel("Go back to previous onboarding step")
            } else {
                Spacer().frame(width: 60)
            }
            
            Spacer()
            
            if !viewModel.isLastStep {
                Button("Skip") {
                    viewModel.skip(onFinish: onComplete)
                }
                .font(AppTypography.subheadline)
                .foregroundStyle(AppColors.textSecondary)
                .accessibilityLabel("Skip onboarding")
            } else {
                Spacer().frame(width: 40)
            }
        }
        .padding(.horizontal, AppSpacing.medium)
        .padding(.top, AppSpacing.small)
    }
    
    private var stepProgressIndicator: some View {
        HStack(spacing: AppSpacing.xSmall) {
            ForEach(0..<viewModel.totalSteps, id: \.self) { index in
                Capsule()
                    .fill(index <= viewModel.stepIndex ? AppColors.primary : Color.white.opacity(0.15))
                    .frame(height: 4)
                    .frame(maxWidth: .infinity)
                    .animation(.easeInOut(duration: 0.3), value: viewModel.stepIndex)
            }
        }
        .padding(.horizontal, AppSpacing.large)
    }
    
    private var bottomControls: some View {
        PrimaryButton(
            title: viewModel.isLastStep ? "Start Exploring" : "Continue",
            iconSystemName: viewModel.isLastStep ? "safari.fill" : "arrow.right"
        ) {
            viewModel.nextStep(onFinish: onComplete)
        }
        .accessibilityLabel(viewModel.isLastStep ? "Start Exploring button" : "Continue to next onboarding step")
    }
}
