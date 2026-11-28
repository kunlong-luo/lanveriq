//! Candidate-only asset selection using the pinned GPUI Kit public macro.
//! Keep the native component path contract without loading the default bundle.
//! Canonical Lucide bytes only; upstream notice/redistribution review stays open.
use gpui_kit::{AssetSource, Result, SharedString};
use std::borrow::Cow;

gpui_kit::assets::icon_assets!(CanonicalIcons, [
    ALargeSmall, ArrowDown, ArrowDownRight, ArrowLeft, ArrowRight, ArrowUp,
    Asterisk, Ban, Battery, BatteryCharging, BatteryFull, BatteryLow,
    BatteryMedium, BatteryWarning, Bell, BookOpen, Bot, Building2,
    Calendar, CaseSensitive, ChartPie, Check, ChevronDown, ChevronLeft,
    ChevronRight, ChevronUp, ChevronsUpDown, CircleAlert, CircleCheck, CircleUser,
    CircleX, Copy, Cpu, Delete, Ellipsis, EllipsisVertical,
    ExternalLink, Eye, EyeOff, File, FileText, Folder,
    FolderClosed, FolderOpen, Frame, GalleryVerticalEnd, Globe, HardDrive,
    Heart, HeartOff, Inbox, Info, LayoutDashboard, Loader,
    LoaderCircle, Map, Maximize, MemoryStick, Menu, Mic,
    Minimize, Minus, Moon, Network, Palette, PanelBottom,
    PanelBottomOpen, PanelLeft, PanelLeftClose, PanelLeftOpen, PanelRight, PanelRightClose,
    PanelRightOpen, Pause, Play, Plus, Redo, Redo2,
    RefreshCw, Replace, RotateCw, Search, Settings, Settings2,
    Square, SquareTerminal, Star, StarOff, Sun, ThumbsDown,
    ThumbsUp, TriangleAlert, Undo, Undo2, User, X,
]);

const ALIASES: &[(&str, &str)] = &[
    ("icons/close.svg", "icons/x.svg"),
    ("icons/dash.svg", "icons/minus.svg"),
    ("icons/github.svg", "icons/external-link.svg"),
    ("icons/inspector.svg", "icons/search.svg"),
    ("icons/resize-corner.svg", "icons/arrow-down-right.svg"),
    ("icons/sort-ascending.svg", "icons/chevron-up.svg"),
    ("icons/sort-descending.svg", "icons/chevron-down.svg"),
    ("icons/star-fill.svg", "icons/star.svg"),
    ("icons/window-close.svg", "icons/x.svg"),
    ("icons/window-maximize.svg", "icons/square.svg"),
    ("icons/window-minimize.svg", "icons/minus.svg"),
    ("icons/window-restore.svg", "icons/copy.svg"),
];

pub struct CandidateAssets;

impl AssetSource for CandidateAssets {
    fn load(&self, path: &str) -> Result<Option<Cow<'static, [u8]>>> {
        let canonical = ALIASES.iter().find(|(old, _)| *old == path)
            .map_or(path, |(_, replacement)| *replacement);
        CanonicalIcons.load(canonical)
    }

    fn list(&self, path: &str) -> Result<Vec<SharedString>> {
        let mut paths = CanonicalIcons.list(path)?;
        paths.extend(ALIASES.iter().filter(|(old, _)| old.starts_with(path))
            .map(|(old, _)| SharedString::from(*old)));
        paths.sort();
        paths.dedup();
        Ok(paths)
    }
}

#[cfg(test)]
mod tests {
    use super::{ALIASES, CandidateAssets, CanonicalIcons};
    use gpui_kit::AssetSource;

    #[test]
    fn aliases_use_only_canonical_payloads() {
        for (old, replacement) in ALIASES {
            assert!(CanonicalIcons.load(old).unwrap().is_none(), "{old}");
            let bytes = CanonicalIcons.load(replacement).unwrap().expect("canonical target");
            assert!(!bytes.is_empty());
            assert_eq!(CandidateAssets.load(old).unwrap().unwrap(), bytes);
        }
    }

    #[test]
    fn selected_paths_are_complete_and_unknown_paths_do_not_load() {
        let paths = CandidateAssets.list("icons/").unwrap();
        assert_eq!(paths.len(), 108); // 94 verified defaults + 2 extras + 12 aliases.
        for path in &paths {
            assert!(CandidateAssets.load(path.as_ref()).unwrap().is_some());
        }
        assert!(CandidateAssets.list("unknown/").unwrap().is_empty());
        assert!(CandidateAssets.load("icons/not-selected.svg").unwrap().is_none());
        assert!(CandidateAssets.load("../icons/x.svg").unwrap().is_none());
    }
}
