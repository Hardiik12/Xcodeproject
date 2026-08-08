//
//  SavedContentStore.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import Foundation
import Combine

public final class SavedContentStore: ObservableObject, @unchecked Sendable {
    public static let shared = SavedContentStore()
    
    private static let userDefaultsKey = "communityott_saved_content_ids"
    
    @Published public private(set) var savedIDs: Set<String> = []
    
    private init() {
        loadSavedIDsFromUserDefaults()
    }
    
    // MARK: - Store Operations
    
    public func save(contentID: String) {
        guard !contentID.isEmpty else { return }
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.savedIDs.insert(contentID)
            self.persistToUserDefaults()
        }
    }
    
    public func remove(contentID: String) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.savedIDs.remove(contentID)
            self.persistToUserDefaults()
        }
    }
    
    public func toggleSave(contentID: String) {
        if isSaved(contentID: contentID) {
            remove(contentID: contentID)
        } else {
            save(contentID: contentID)
        }
    }
    
    public func isSaved(contentID: String) -> Bool {
        savedIDs.contains(contentID)
    }
    
    public func clearAll() {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.savedIDs.removeAll()
            self.persistToUserDefaults()
        }
    }
    
    // MARK: - Private Helpers
    
    private func loadSavedIDsFromUserDefaults() {
        if let array = UserDefaults.standard.stringArray(forKey: Self.userDefaultsKey) {
            self.savedIDs = Set(array)
        }
    }
    
    private func persistToUserDefaults() {
        let array = Array(savedIDs)
        UserDefaults.standard.set(array, forKey: Self.userDefaultsKey)
    }
}
