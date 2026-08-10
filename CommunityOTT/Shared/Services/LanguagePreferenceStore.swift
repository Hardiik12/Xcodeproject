//
//  LanguagePreferenceStore.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import Foundation
import Combine

public enum AppLanguage: String, Codable, CaseIterable, Identifiable, Sendable {
    case english = "en"
    case telugu = "te"
    
    public var id: String { rawValue }
    
    public var displayName: String {
        switch self {
        case .english: return "English"
        case .telugu: return "తెలుగు"
        }
    }
}

public final class LanguagePreferenceStore: ObservableObject, @unchecked Sendable {
    public static let shared = LanguagePreferenceStore()
    
    private static let userDefaultsKey = "communityott_language_preference"
    
    @Published public private(set) var currentLanguage: AppLanguage = .english
    
    private let translations: [String: [AppLanguage: String]] = [
        "Home": [.english: "Home", .telugu: "హోమ్"],
        "Discover": [.english: "Discover", .telugu: "డిస్కవర్"],
        "Search": [.english: "Search", .telugu: "సెర్చ్"],
        "Saved": [.english: "Saved", .telugu: "సేవ్ చేసినవి"],
        "Profile": [.english: "Profile", .telugu: "ప్రొఫైల్"],
        "Settings": [.english: "Settings", .telugu: "సెట్టింగ్స్"],
        "My List": [.english: "My List", .telugu: "నా లిస్ట్"],
        "Language": [.english: "Language", .telugu: "భాష"],
        "Notifications": [.english: "Notifications", .telugu: "నోటిఫికేషన్లు"],
        "Playback": [.english: "Playback", .telugu: "ప్లేబ్యాక్"],
        "Sign Out": [.english: "Sign Out", .telugu: "సైన్ అవుట్"],
        "About CommunityOTT": [.english: "About CommunityOTT", .telugu: "కమ్యూనిటీ ఓటీటీ గురించి"],
        "Explore Content": [.english: "Explore Content", .telugu: "కంటెంట్‌ను అన్వేషించండి"],
        "Watch Now": [.english: "Watch Now", .telugu: "ఇప్పుడే చూడండి"],
        "Get Started": [.english: "Get Started", .telugu: "ప్రారంభించండి"],
        "Already a member?": [.english: "Already a member?", .telugu: "ఇప్పటికే సభ్యులా?"],
        "Sign In": [.english: "Sign In", .telugu: "సైన్ ఇన్"],
        "Continue as Guest": [.english: "Continue as Guest", .telugu: "గెస్ట్‌గా కొనసాగండి"],
        "Create Account": [.english: "Create Account", .telugu: "ఖాతాను సృష్టించండి"],
        "Welcome back": [.english: "Welcome back", .telugu: "తిరిగి స్వాగతం"],
        "Create your account": [.english: "Create your account", .telugu: "మీ ఖాతాను సృష్టించండి"],
        "Verify your account": [.english: "Verify your account", .telugu: "మీ ఖాతాను సరిచూడండి"],
        "Folk & Cultural Arts": [.english: "Folk & Cultural Arts", .telugu: "జానపద & సాంస్కృతిక కళలు"],
        "History & Documentaries": [.english: "History & Documentaries", .telugu: "చరిత్ర & డాక్యుమెంటరీలు"],
        "Empowerment": [.english: "Empowerment", .telugu: "సాధికారత"],
        "Voices of Success": [.english: "Voices of Success", .telugu: "విజయ గాథలు"],
        "Podcasts": [.english: "Podcasts", .telugu: "పాడ్‌కాస్ట్‌లు"],
        "Education & Skills": [.english: "Education & Skills", .telugu: "విద్య & నైపుణ్యాలు"],
        "Continue Watching": [.english: "Continue Watching", .telugu: "చూడటం కొనసాగించండి"],
        "Featured Stories": [.english: "Featured Stories", .telugu: "ప్రత్యేక కథనాలు"],
        "Clear Filters": [.english: "Clear Filters", .telugu: "ఫిల్టర్‌లను క్లియర్ చేయండి"]
    ]
    
    private init() {
        loadLanguageFromUserDefaults()
    }
    
    public func setLanguage(_ language: AppLanguage) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.objectWillChange.send()
            self.currentLanguage = language
            self.persistToUserDefaults()
        }
    }
    
    public func localizedString(for key: String) -> String {
        guard let map = translations[key], let value = map[currentLanguage] else {
            return key
        }
        return value
    }
    
    private func loadLanguageFromUserDefaults() {
        if let raw = UserDefaults.standard.string(forKey: Self.userDefaultsKey),
           let lang = AppLanguage(rawValue: raw) {
            self.currentLanguage = lang
        }
    }
    
    private func persistToUserDefaults() {
        UserDefaults.standard.set(currentLanguage.rawValue, forKey: Self.userDefaultsKey)
    }
}
