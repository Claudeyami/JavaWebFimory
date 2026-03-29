import React, { useEffect, useState, useMemo } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import CommentSection from '../components/content/CommentSection';
import { saveSeriesHistory, awardChapterCompleteExp, fetchCurrentRole } from '../lib/api';
import { BookOpen, Calendar, User, Star, Eye, Heart, ChevronLeft, ChevronRight, ChevronDown, Share2, Home, ArrowUp } from 'lucide-react';
import { buildMediaUrl } from '../lib/config';

interface ChapterImage {
  ImageID: number;
  ImageURL: string;
  ImageOrder: number;
  FileSize?: number;
  Width?: number;
  Height?: number;
}

interface Chapter {
  ChapterID: number;
  ChapterNumber: number;
  Title: string;
  Content: string;
  IsFree: boolean;
  CreatedAt: string;
  StoryType?: 'Text' | 'Comic';
  Images?: ChapterImage[];
  ImageCount?: number;
  ViewCount?: number;
}

interface Story {
  SeriesID: number;
  Title: string;
  Slug: string;
  Description: string;
  CoverURL: string;
  Author?: string;
  Status: string;
  IsFree: boolean;
  ViewCount?: number;
  Rating?: number;
  StoryType?: 'Text' | 'Comic';
}

const unwrapApiData = <T,>(payload: any): T => (payload?.data ?? payload) as T;

const fixMojibake = (value?: string | null): string => {
  if (!value) return '';
  const text = String(value);
  if (!/[ÃÆÂ]/.test(text)) return text;
  try {
    const bytes = Uint8Array.from(Array.from(text).map((ch) => ch.charCodeAt(0) & 0xff));
    const decoded = new TextDecoder('utf-8', { fatal: false }).decode(bytes);
    return decoded || text;
  } catch {
    return text;
  }
};

const normalizeStoryData = (raw: Story): Story => ({
  ...raw,
  Title: fixMojibake(raw?.Title),
  Description: fixMojibake(raw?.Description),
  Author: fixMojibake(raw?.Author || ''),
  Status: raw?.Status || 'Pending',
  IsFree: typeof raw?.IsFree === 'boolean' ? raw.IsFree : true,
});

const normalizeChapters = (rawList: Chapter[]): Chapter[] =>
  (Array.isArray(rawList) ? rawList : []).map((chapter) => ({
    ...chapter,
    Title: fixMojibake(chapter?.Title),
    Content: chapter?.Content || '',
    Images: Array.isArray(chapter?.Images)
      ? chapter.Images.map((img) => ({ ...img, ImageURL: fixMojibake(img?.ImageURL) }))
      : [],
  }));

const buildChapterDisplayTitle = (chapter: Chapter): string => {
  const chapterNumber = chapter?.ChapterNumber ?? 0;
  const genericLabel = `Chapter ${chapterNumber}`;
  const rawTitle = fixMojibake(chapter?.Title || '').trim();
  if (!rawTitle) return genericLabel;

  const lower = rawTitle.toLowerCase();
  if (
    lower === `chapter ${chapterNumber}` ||
    lower === `chuong ${chapterNumber}` ||
    lower === `chương ${chapterNumber}` ||
    lower === `${chapterNumber}`
  ) {
    return genericLabel;
  }

  return rawTitle;
};

const StoryPage: React.FC = () => {
  const navigate = useNavigate();
  const { slug } = useParams<{ slug: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const { user } = useAuth();
  const [story, setStory] = useState<Story | null>(null);
  const [chapters, setChapters] = useState<Chapter[]>([]);
  const [currentChapter, setCurrentChapter] = useState<Chapter | null>(null);
  const [loading, setLoading] = useState(true);
  const [ratings, setRatings] = useState<{ averageRating: number; totalRatings: number } | null>(null);
  const [myRating, setMyRating] = useState<number>(0);
  const [isFavorite, setIsFavorite] = useState(false);
  const [showChapterDropdown, setShowChapterDropdown] = useState(false); // Dropdown chọn chương
  const [hasAwardedExp, setHasAwardedExp] = useState<boolean>(false);
  const [isAdmin, setIsAdmin] = useState<boolean>(false);
  const [showReaderQuickNav, setShowReaderQuickNav] = useState<boolean>(false);

  const email = useMemo(
    () => (user?.email as string | undefined) || "",
    [user]
  );

  useEffect(() => {
    const loadStory = async () => {
      if (!slug) return;
      
      console.log('[StoryPage] Loading story with slug:', slug);
      setLoading(true);
      try {
        // Load story details
        const storyResponse = await fetch(`/api/stories/${slug}`);
        console.log('[StoryPage] Story API response:', storyResponse.status);
        if (storyResponse.ok) {
          const storyPayload = await storyResponse.json();
          const storyData = normalizeStoryData(unwrapApiData<Story>(storyPayload));
          if (!storyData || !storyData.SeriesID) {
            throw new Error('Dữ liệu truyện không hợp lệ từ server');
          }
          console.log('[StoryPage] Story data:', storyData);
          setStory(storyData);
          
          // Load chapters
          const chaptersResponse = await fetch(`/api/stories/${storyData.SeriesID}/chapters`);
          console.log('[StoryPage] Chapters API response:', chaptersResponse.status);
          if (chaptersResponse.ok) {
            const chaptersPayload = await chaptersResponse.json();
            const chaptersData = normalizeChapters(unwrapApiData<Chapter[]>(chaptersPayload));
            console.log('[StoryPage] Chapters data:', chaptersData);
            setChapters(chaptersData);
            if (chaptersData.length > 0) {
              // Check if there's a chapter query parameter
              const chapterParam = searchParams.get('chapter');
              if (chapterParam) {
                const chapterNumber = parseInt(chapterParam, 10);
                const targetChapter = chaptersData.find(
                  (ch: Chapter) => ch.ChapterNumber === chapterNumber
                );
                if (targetChapter) {
                  setCurrentChapter(targetChapter);
                } else {
                  setCurrentChapter(null);
                }
              } else {
                setCurrentChapter(null);
              }
            } else {
              setCurrentChapter(null);
            }
          }
          
          // Load ratings
          const ratingsResponse = await fetch(`/api/stories/${storyData.SeriesID}/ratings`);
          if (ratingsResponse.ok) {
            const ratingsPayload = await ratingsResponse.json();
            const ratingsData = ratingsPayload?.data ?? ratingsPayload ?? {};
            setRatings({
              averageRating: Number(ratingsData.averageRating ?? ratingsData.average ?? 0) || 0,
              totalRatings: Number(ratingsData.totalRatings ?? ratingsData.count ?? 0) || 0,
            });
          }
          
          // Check if user has rated this story
          if (user?.email) {
            try {
              const userRatingResponse = await fetch(`/api/stories/${storyData.SeriesID}/user-rating`, {
                headers: { 'x-user-email': user.email }
              });
              if (userRatingResponse.ok) {
                const userRatingPayload = await userRatingResponse.json();
                const userRatingData = userRatingPayload?.data ?? userRatingPayload ?? {};
                setMyRating(Number(userRatingData.rating ?? 0) || 0);
              }
              
              // Check if story is in favorites
              const favoriteResponse = await fetch(`/api/stories/${storyData.SeriesID}/favorite-status`, {
                headers: { 'x-user-email': user.email }
              });
              if (favoriteResponse.ok) {
                const favoritePayload = await favoriteResponse.json();
                const favoriteData = favoritePayload?.data ?? favoritePayload ?? {};
                setIsFavorite(Boolean(favoriteData.isFavorite));
              }
            } catch (error) {
              console.error('Error loading user data:', error);
            }
          }
        }
      } catch (error) {
        console.error('Error loading story:', error);
      } finally {
        setLoading(false);
      }
    };

    loadStory();
  }, [slug, searchParams, user?.email]);

  // Scroll to comments section if URL hash is #comments
  useEffect(() => {
    if (!loading && story && window.location.hash === '#comments') {
      const scrollToComments = () => {
        const commentsSection = document.getElementById('comments');
        if (commentsSection) {
          // Tính toán offset để trừ đi chiều cao header (nếu có fixed header)
          const headerOffset = 80;
          const elementPosition = commentsSection.getBoundingClientRect().top;
          const offsetPosition = elementPosition + window.pageYOffset - headerOffset;

          window.scrollTo({
            top: offsetPosition,
            behavior: 'smooth'
          });
          return true;
        }
        return false;
      };

      // Thử ngay lập tức
      if (scrollToComments()) {
        return; // Đã scroll thành công
      }

      // Nếu chưa có, thử lại sau một khoảng thời gian ngắn
      const timeouts: NodeJS.Timeout[] = [];
      const attempts = [100, 300, 500];
      
      attempts.forEach((delay) => {
        const timeout = setTimeout(() => {
          if (scrollToComments()) {
            // Đã tìm thấy và scroll, clear các timeout còn lại
            timeouts.forEach(t => clearTimeout(t));
          }
        }, delay);
        timeouts.push(timeout);
      });

      return () => {
        timeouts.forEach(t => clearTimeout(t));
      };
    }
  }, [loading, story]);

  const handleChapterSelect = (chapter: Chapter) => {
    setCurrentChapter(chapter);
    setSearchParams({ chapter: String(chapter.ChapterNumber) });
    // Scroll to top when chapter changes
    window.scrollTo({ top: 0, behavior: 'smooth' });
    // Reset EXP flag khi chuyển chapter
    setHasAwardedExp(false);
  };

  // Load user role to check if Admin
  useEffect(() => {
    if (!email) return;
    fetchCurrentRole(email)
      .then(({ role }) => setIsAdmin(role === 'Admin'))
      .catch(() => setIsAdmin(false));
  }, [email]);

  // Save read history when chapter changes
  useEffect(() => {
    if (!email || !story?.SeriesID || !currentChapter?.ChapterID) return;
    saveSeriesHistory(email, Number(story.SeriesID), currentChapter.ChapterID)
      .then(() => {
        setStory((prev) => {
          if (!prev) return prev;
          return { ...prev, ViewCount: Number(prev.ViewCount || 0) + 1 };
        });
      })
      .catch(() => {});
    // Reset EXP flag khi chuyển chapter
    setHasAwardedExp(false);
  }, [email, story?.SeriesID, currentChapter?.ChapterID]);

  // Track scroll để phát hiện đọc hết chapter (cho text story)
  useEffect(() => {
    if (!email || !currentChapter || hasAwardedExp || story?.StoryType === 'Comic' || isAdmin) return;

    const handleScroll = () => {
      const windowHeight = window.innerHeight;
      const documentHeight = document.documentElement.scrollHeight;
      const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
      
      // Kiểm tra đã scroll đến gần cuối (còn 100px)
      const isNearBottom = scrollTop + windowHeight >= documentHeight - 100;
      
      if (isNearBottom && !hasAwardedExp) {
        // Đọc hết chapter, tăng EXP
        awardChapterCompleteExp(email, currentChapter.ChapterID)
          .then((response) => {
            if (response.expGained > 0) {
              setHasAwardedExp(true);
              console.log(`Nhận được ${response.expGained} EXP!`);
            }
          })
          .catch((error) => {
            console.error("Error awarding chapter complete EXP:", error);
          });
      }
    };

    window.addEventListener('scroll', handleScroll);
    // Kiểm tra ngay khi load nếu đã ở cuối trang
    handleScroll();

    return () => {
      window.removeEventListener('scroll', handleScroll);
    };
  }, [email, currentChapter, hasAwardedExp, story?.StoryType, isAdmin]);

  // Track việc xem hết ảnh (cho comic story)
  useEffect(() => {
    if (!email || !currentChapter || hasAwardedExp || story?.StoryType !== 'Comic' || isAdmin) return;
    if (!currentChapter.Images || currentChapter.Images.length === 0) return;

    // Kiểm tra khi scroll đến ảnh cuối cùng
    const handleScroll = () => {
      const lastImage = document.querySelector(`img[data-image-id="${currentChapter.Images![currentChapter.Images!.length - 1].ImageID}"]`);
      if (lastImage) {
        const rect = lastImage.getBoundingClientRect();
        // Ảnh cuối đã hiển thị trên màn hình
        if (rect.top < window.innerHeight && rect.bottom > 0 && !hasAwardedExp) {
          awardChapterCompleteExp(email, currentChapter.ChapterID)
            .then((response) => {
              if (response.expGained > 0) {
                setHasAwardedExp(true);
                console.log(`Nhận được ${response.expGained} EXP!`);
              }
            })
            .catch((error) => {
              console.error("Error awarding chapter complete EXP:", error);
            });
        }
      }
    };

    window.addEventListener('scroll', handleScroll);
    // Kiểm tra ngay khi load
    setTimeout(handleScroll, 500);

    return () => {
      window.removeEventListener('scroll', handleScroll);
    };
  }, [email, currentChapter, hasAwardedExp, story?.StoryType, isAdmin]);

  // Hiện thanh điều hướng nhanh khi cuộn xuống
  useEffect(() => {
    const handleScrollVisibility = () => {
      setShowReaderQuickNav(window.scrollY > 500);
    };

    handleScrollVisibility();
    window.addEventListener('scroll', handleScrollVisibility);
    return () => {
      window.removeEventListener('scroll', handleScrollVisibility);
    };
  }, []);

  // Sắp xếp chapters mặc định từ 1 lên (1, 2, 3...)
  const sortedChapters = useMemo(() => {
    // Sort theo ChapterNumber tăng dần (1, 2, 3...)
    return [...chapters].sort((a, b) => a.ChapterNumber - b.ChapterNumber);
  }, [chapters]);

  // Navigate to next/previous chapter - logic đúng
  // Next = chương số cao hơn (index + 1), Prev = chương số thấp hơn (index - 1)
  const currentChapterIndex = sortedChapters.findIndex(
    (ch) => ch.ChapterID === currentChapter?.ChapterID
  );
  
  // sortOrder = 'asc' (1, 2, 3...): next = index + 1, prev = index - 1
  const nextChapter = currentChapterIndex >= 0 && currentChapterIndex < sortedChapters.length - 1
    ? sortedChapters[currentChapterIndex + 1]
    : null;
  
  const prevChapter = currentChapterIndex > 0
    ? sortedChapters[currentChapterIndex - 1]
    : null;


  const handleRate = async (value: number) => {
    if (!user?.email || !story) return;
    setMyRating(value);
    
    try {
      const response = await fetch(`/api/stories/${story.SeriesID}/rating`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-user-email': user.email,
        },
        body: JSON.stringify({ rating: value }),
      });

      if (response.ok) {
        // Reload ratings
        const ratingsResponse = await fetch(`/api/stories/${story.SeriesID}/ratings`);
        if (ratingsResponse.ok) {
          const ratingsPayload = await ratingsResponse.json();
          const ratingsData = ratingsPayload?.data ?? ratingsPayload ?? {};
          setRatings({
            averageRating: Number(ratingsData.averageRating ?? ratingsData.average ?? 0) || 0,
            totalRatings: Number(ratingsData.totalRatings ?? ratingsData.count ?? 0) || 0,
          });
        }
      }
    } catch (error) {
      console.error('Error rating story:', error);
    }
  };

  const handleToggleFavorite = async () => {
    if (!user?.email || !story) return;
    
    try {
      const seriesId = story.SeriesID;
      if (isFavorite) {
        // Remove from favorites
        await fetch(`/api/stories/${seriesId}/favorite`, {
          method: 'DELETE',
          headers: { 'x-user-email': user.email },
        });
      } else {
        // Add to favorites
        await fetch(`/api/stories/${seriesId}/favorite`, {
          method: 'POST',
          headers: { 'x-user-email': user.email },
        });
      }
      
      // Verify status sau khi toggle để đảm bảo đồng bộ
      const response = await fetch(`/api/stories/${seriesId}/favorite-status`, {
        headers: { 'x-user-email': user.email },
      });
      if (response.ok) {
        const payload = await response.json();
        const data = unwrapApiData<{ isFavorite?: boolean }>(payload);
        setIsFavorite(Boolean(data?.isFavorite));
      }
    } catch (error) {
      console.error('Error toggling favorite:', error);
    }
  };

  const shareStory = async (platform: 'facebook' | 'zalo') => {
    if (!story) return;
    const shareUrl = window.location.href;
    const shareTitle = `${story.Title} - Fimory`;
    if (navigator.share) {
      try {
        await navigator.share({
          title: shareTitle,
          text: `Đọc ${story.Title} trên Fimory`,
          url: shareUrl,
        });
        return;
      } catch {
        // ignore and fallback to social links
      }
    }

    const encodedUrl = encodeURIComponent(shareUrl);
    const encodedText = encodeURIComponent(`Đọc ${story.Title} trên Fimory`);
    const target = platform === 'facebook'
      ? `https://www.facebook.com/sharer/sharer.php?u=${encodedUrl}&quote=${encodedText}`
      : `https://zalo.me/share?url=${encodedUrl}`;
    window.open(target, '_blank', 'noopener,noreferrer');
  };

  if (loading) {
    return (
      <div className="max-w-6xl mx-auto px-4 py-10 text-gray-600 dark:text-gray-300">
        Đang tải...
      </div>
    );
  }

  if (!story) {
    return (
      <div className="max-w-6xl mx-auto px-4 py-10 text-gray-600 dark:text-gray-300">
        Không tìm thấy truyện.
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      {/* Story Header - Fixed at top for comic reading */}
      {story?.StoryType === 'Comic' && currentChapter && (
        <div className="sticky top-0 z-40 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 shadow-sm">
          <div className="max-w-7xl mx-auto px-4 py-3">
            <div className="flex items-center justify-between gap-4 flex-wrap">
              <div className="flex items-center gap-4 flex-1 min-w-0">
                <h1 className="text-lg font-semibold text-gray-900 dark:text-white truncate">
                  {story.Title}
                </h1>
                <span className="text-sm text-gray-500 dark:text-gray-400 truncate">
                  {buildChapterDisplayTitle(currentChapter)}
                </span>
              </div>
              
              {/* Dropdown chọn chương cho Comic */}
              <div className="relative flex-1 min-w-[200px] max-w-[300px]">
                <button
                  onClick={() => setShowChapterDropdown(!showChapterDropdown)}
                  className="w-full px-4 py-2 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg text-left flex items-center justify-between hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                >
                  <span className="font-medium text-gray-900 dark:text-white text-sm truncate">
                    {buildChapterDisplayTitle(currentChapter)}
                  </span>
                  <ChevronDown className={`w-4 h-4 text-gray-500 transition-transform flex-shrink-0 ${showChapterDropdown ? 'rotate-180' : ''}`} />
                </button>
                
                {/* Dropdown menu */}
                {showChapterDropdown && (
                  <>
                    <div 
                      className="fixed inset-0 z-40" 
                      onClick={() => setShowChapterDropdown(false)}
                    />
                    <div className="absolute top-full left-0 right-0 mt-2 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg shadow-lg max-h-96 overflow-y-auto z-50">
                      {sortedChapters.map((chapter) => (
                        <button
                          key={chapter.ChapterID}
                          onClick={() => {
                            handleChapterSelect(chapter);
                            setShowChapterDropdown(false);
                          }}
                          className={`w-full text-left px-4 py-2 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors ${
                            currentChapter?.ChapterID === chapter.ChapterID
                              ? "bg-blue-50 dark:bg-blue-950/30 text-blue-600 dark:text-blue-400 font-medium"
                              : "text-gray-900 dark:text-gray-100"
                          }`}
                        >
                          {buildChapterDisplayTitle(chapter)}
                        </button>
                      ))}
                    </div>
                  </>
                )}
              </div>
              
              <div className="flex items-center gap-2">
                {prevChapter && (
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => handleChapterSelect(prevChapter)}
                  >
                    <ChevronLeft className="w-4 h-4 mr-1" />
                    Trước
                  </Button>
                )}
                {nextChapter && (
                  <Button
                    size="sm"
                    variant="primary"
                    onClick={() => handleChapterSelect(nextChapter)}
                  >
                    Sau
                    <ChevronRight className="w-4 h-4 ml-1" />
                  </Button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
        <div className="grid gap-6 lg:grid-cols-1">
          {/* Story Info + Content */}
          <div>
          {/* Story Header */}
          <div className="flex items-start gap-4 mb-6">
            <div className="w-32 h-48 bg-gray-200 dark:bg-gray-700 rounded-lg overflow-hidden flex-shrink-0">
              {story.CoverURL ? (
                <img 
                  src={buildMediaUrl(story.CoverURL) || undefined} 
                  alt={story.Title}
                  className="w-full h-full object-cover"
                />
              ) : (
                <div className="w-full h-full flex items-center justify-center">
                  <BookOpen className="w-8 h-8 text-gray-400" />
                </div>
              )}
            </div>
            
            <div className="flex-1">
              <h1 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">
                {story.Title}
              </h1>
              
              <div className="flex items-center gap-4 text-sm text-gray-600 dark:text-gray-400 mb-3">
                <div className="flex items-center gap-1">
                  <User className="w-4 h-4" />
                  <span>Tác giả: {story.Author || 'Đang cập nhật'}</span>
                </div>
                <div className="flex items-center gap-1">
                  <Eye className="w-4 h-4" />
                  <span>{Number(story.ViewCount || 0).toLocaleString()} lượt xem</span>
                </div>
                <div className="flex items-center gap-1">
                  <Star className="w-4 h-4" />
                  <span>{story.Rating?.toFixed(1) || '0.0'}</span>
                </div>
              </div>
              
              <div className="flex items-center gap-2 mb-3">
                <span className={`px-2 py-1 rounded-full text-xs font-medium ${
                  story.Status === 'Approved' 
                    ? 'bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200'
                    : story.Status === 'Pending'
                    ? 'bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200'
                    : 'bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-200'
                }`}>
                  {story.Status === 'Approved' ? 'Đã duyệt' :
                   story.Status === 'Pending' ? 'Chờ duyệt' : 'Bị từ chối'}
                </span>
                <span className={`px-2 py-1 rounded-full text-xs font-medium ${
                  story.IsFree 
                    ? 'bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200'
                    : 'bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200'
                }`}>
                  {story.IsFree ? 'Miễn phí' : 'Trả phí'}
                </span>
              </div>
              
              {/* Rating */}
              <div className="mt-3 flex items-center gap-3 text-sm text-gray-700 dark:text-gray-300">
                <div className="flex items-center gap-1">
                  <Star className="w-4 h-4" />
                  <span>{ratings?.averageRating?.toFixed(1) || '0.0'} ({ratings?.totalRatings || 0})</span>
                </div>
                {user?.email && (
                  <div className="flex items-center gap-1">
                    {[1,2,3,4,5].map(n => (
                      <button
                        key={n}
                        onClick={() => handleRate(n)}
                        className={`text-xl ${n <= myRating ? 'text-yellow-400' : 'text-gray-400'}`}
                        aria-label={`rate-${n}`}
                      >
                        ★
                      </button>
                    ))}
                  </div>
                )}
              </div>
              
              {/* Favorite Button */}
              {user?.email && (
                <div className="mt-3">
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={handleToggleFavorite}
                  >
                    <Heart className={`w-4 h-4 mr-2 ${isFavorite ? 'text-red-500 fill-current' : ''}`} />
                    {isFavorite ? 'Đã yêu thích' : 'Yêu thích'}
                  </Button>
                </div>
              )}
              <div className="mt-3 flex items-center gap-2">
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => shareStory('facebook')}
                >
                  <Share2 className="w-4 h-4 mr-2" />
                  Share Facebook
                </Button>
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => shareStory('zalo')}
                >
                  <Share2 className="w-4 h-4 mr-2" />
                  Share Zalo
                </Button>
              </div>
              
              <p className="text-gray-700 dark:text-gray-300">
                {story.Description}
              </p>
            </div>
          </div>

          {/* Chapter List - Traditional story flow: chọn chương trước khi đọc */}
          {sortedChapters.length > 0 && (
            <Card className="mb-6">
              <div className="p-5">
                <div className="flex items-center justify-between mb-3">
                  <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                    Chapter list ({sortedChapters.length})
                  </h2>
                  {sortedChapters.length > 0 && (
                    <Button
                      size="sm"
                      variant="primary"
                      onClick={() => handleChapterSelect(sortedChapters[sortedChapters.length - 1])}
                    >
                      Read latest chapter
                    </Button>
                  )}
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2 max-h-80 overflow-y-auto pr-1">
                  {sortedChapters.map((chapter) => (
                    <button
                      key={chapter.ChapterID}
                      onClick={() => handleChapterSelect(chapter)}
                      className={`text-left rounded-lg border px-3 py-2 transition-colors ${
                        currentChapter?.ChapterID === chapter.ChapterID
                          ? 'border-blue-500 bg-blue-50 dark:bg-blue-950/30 text-blue-700 dark:text-blue-300'
                          : 'border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 text-gray-800 dark:text-gray-200'
                      }`}
                    >
                      <div className="text-sm font-semibold truncate">{buildChapterDisplayTitle(chapter)}</div>
                    </button>
                  ))}
                </div>
              </div>
            </Card>
          )}

          {/* Chapter Content */}
          {currentChapter ? (
            <div className={`mb-6 ${story.StoryType === 'Comic' ? '' : ''}`}>
              {story.StoryType !== 'Comic' && (
                <Card className="mb-4">
                  <div className="p-6">
                    <div className="flex items-center justify-between mb-4">
                      <h2 className="text-xl font-semibold text-gray-900 dark:text-white">
                        {buildChapterDisplayTitle(currentChapter)}
                      </h2>
                      <div className="flex items-center gap-4 text-sm text-gray-500 dark:text-gray-400">
                        <div className="flex items-center gap-1">
                          <Eye className="w-4 h-4" />
                          <span>{currentChapter.ViewCount?.toLocaleString() || 0} lượt xem</span>
                        </div>
                        <div className="flex items-center gap-1">
                          <Calendar className="w-4 h-4" />
                          <span>{new Date(currentChapter.CreatedAt).toLocaleDateString()}</span>
                        </div>
                        {currentChapter.ImageCount !== undefined && currentChapter.ImageCount > 0 && (
                          <span>• {currentChapter.ImageCount} trang</span>
                        )}
                      </div>
                    </div>
                  </div>
                </Card>
              )}

              {/* Navigation for Text Stories */}
              {story.StoryType !== 'Comic' && (
                <div className="flex items-center justify-between mb-4 gap-2">
                  {prevChapter ? (
                    <Button
                      variant="secondary"
                      onClick={() => handleChapterSelect(prevChapter)}
                      className="flex items-center gap-2"
                    >
                      <ChevronLeft className="w-4 h-4" />
                      Prev chapter
                    </Button>
                  ) : (
                    <div></div>
                  )}
                  {nextChapter && (
                    <Button
                      variant="primary"
                      onClick={() => handleChapterSelect(nextChapter)}
                      className="flex items-center gap-2"
                    >
                      Next chapter
                      <ChevronRight className="w-4 h-4" />
                    </Button>
                  )}
                </div>
              )}

              {story.StoryType !== 'Comic' && (
                <Card>
                  <div className="p-6">
                    {/* Text Story Content */}
                    {story.StoryType === 'Text' && (
                <div className="prose dark:prose-invert max-w-none">
                    {/* Kiểm tra nếu Content là file PDF (đường dẫn file) */}
                    {(() => {
                      const content = currentChapter.Content || '';
                      const isPDFFile = content.startsWith('/storage/') && 
                                       (content.toLowerCase().endsWith('.pdf') || 
                                        content.toLowerCase().includes('.pdf'));
                      
                      if (isPDFFile) {
                        // Hiển thị PDF viewer
                        return (
                          <div className="w-full">
                            <div className="bg-gray-100 dark:bg-gray-800 rounded-lg p-2 mb-2 flex items-center justify-between">
                              <span className="text-sm text-gray-600 dark:text-gray-400">
                                Tài liệu PDF
                              </span>
                              <a
                                href={buildMediaUrl(content) || undefined}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="text-sm text-blue-600 dark:text-blue-400 hover:underline"
                              >
                                Mở trong tab mới
                              </a>
                            </div>
                            <div className="w-full border border-gray-300 dark:border-gray-700 rounded-lg overflow-hidden" style={{ minHeight: '600px' }}>
                              <iframe
                                src={buildMediaUrl(content) || undefined}
                                className="w-full border-0"
                                style={{ minHeight: '600px', height: '80vh' }}
                                title={`PDF Viewer - ${buildChapterDisplayTitle(currentChapter)}`}
                              >
                                <div className="p-4 text-center">
                                  <p className="text-gray-600 dark:text-gray-400 mb-4">
                                    Trình duyệt của bạn không hỗ trợ hiển thị PDF.
                                  </p>
                                  <a
                                    href={buildMediaUrl(content) || undefined}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="text-blue-600 dark:text-blue-400 hover:underline"
                                  >
                                    Tải xuống PDF
                                  </a>
                                </div>
                              </iframe>
                            </div>
                          </div>
                        );
                      } else {
                        // Hiển thị text content như bình thường
                        return (
                  <div className="whitespace-pre-wrap text-gray-700 dark:text-gray-300 leading-relaxed">
                            {content}
                          </div>
                        );
                      }
                    })()}
                    </div>
                  )}
                  </div>
                </Card>
              )}

              {/* Comic Story Images - Full Width */}
              {story.StoryType !== undefined && story.StoryType === 'Comic' && currentChapter.Images && currentChapter.Images.length > 0 && (
                <div className="w-full bg-white dark:bg-gray-900">
                  {/* Chapter Info Bar for Comic */}
                  <div className="sticky top-[60px] z-30 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 px-4 py-2 flex items-center justify-between text-sm text-gray-600 dark:text-gray-400">
                    <div className="flex items-center gap-4">
                      <span>{buildChapterDisplayTitle(currentChapter)}</span>
                      <span>•</span>
                      <span>{currentChapter.ImageCount || currentChapter.Images.length} trang</span>
                      <span>•</span>
                      <span className="flex items-center gap-1">
                        <Eye className="w-4 h-4" />
                        {currentChapter.ViewCount?.toLocaleString() || 0} lượt xem
                      </span>
                    </div>
                  </div>
                  
                  {/* Images - Full Width */}
                  <div className="w-full">
                    {currentChapter.Images.map((image, index) => (
                      <div key={image.ImageID} className="w-full flex justify-center bg-gray-100 dark:bg-gray-800">
                        <img
                          data-image-id={image.ImageID}
                          src={buildMediaUrl(image.ImageURL) || undefined}
                          alt={`${buildChapterDisplayTitle(currentChapter)} - Trang ${index + 1}`}
                          className="max-w-full h-auto"
                          loading="lazy"
                          onError={(e) => {
                            const target = e.target as HTMLImageElement;
                            target.src = '/placeholder-image.png';
                            target.alt = 'Không thể tải ảnh';
                          }}
                        />
                      </div>
                    ))}
                  </div>

                  {/* Bottom Navigation for Comic */}
                  <div className="sticky bottom-0 z-30 bg-white dark:bg-gray-800 border-t border-gray-200 dark:border-gray-700 px-4 py-3">
                    <div className="max-w-4xl mx-auto flex items-center justify-between">
                      {prevChapter ? (
                        <Button
                          variant="secondary"
                          onClick={() => handleChapterSelect(prevChapter)}
                          className="flex items-center gap-2"
                        >
                          <ChevronLeft className="w-4 h-4" />
                          Prev chapter
                        </Button>
                      ) : (
                        <div></div>
                      )}
                      {nextChapter && (
                        <Button
                          variant="primary"
                          onClick={() => handleChapterSelect(nextChapter)}
                          className="flex items-center gap-2"
                        >
                          Next chapter
                          <ChevronRight className="w-4 h-4" />
                        </Button>
                      )}
                    </div>
                  </div>
                </div>
              )}
            </div>
          ) : (
            <Card className="mb-6">
              <div className="p-6 text-center">
                <BookOpen className="w-12 h-12 text-gray-400 mx-auto mb-4" />
                <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-2">
                  {sortedChapters.length > 0 ? 'Select a chapter to start reading' : 'No chapter yet'}
                </h3>
                <p className="text-gray-500 dark:text-gray-400">
                  {sortedChapters.length > 0
                    ? 'Choose a chapter from the list above to open the reader.'
                    : 'This story has no published chapter yet.'}
                </p>
              </div>
            </Card>
          )}

          {/* Comments */}
          <CommentSection 
            contentType="series"
            contentId={story.SeriesID}
            contentTitle={story.Title}
          />
          </div>

          {/* Đã bỏ sidebar danh sách chương - thay bằng dropdown */}
        </div>
      </div>

      {showReaderQuickNav && (
        <div className="fixed bottom-4 left-1/2 -translate-x-1/2 z-50">
          <div className="flex items-center gap-2 rounded-full border border-gray-200 dark:border-gray-700 bg-white/95 dark:bg-gray-900/95 backdrop-blur px-2 py-2 shadow-lg">
            <Button
              size="sm"
              variant="secondary"
              onClick={() => navigate('/')}
              className="rounded-full px-3"
              title="Về trang chủ"
            >
              <Home className="w-4 h-4" />
            </Button>
            {prevChapter && (
              <Button
                size="sm"
                variant="secondary"
                onClick={() => handleChapterSelect(prevChapter)}
                className="rounded-full px-3"
                title="Prev chapter"
              >
                <ChevronLeft className="w-4 h-4" />
              </Button>
            )}
            {nextChapter && (
              <Button
                size="sm"
                variant="secondary"
                onClick={() => handleChapterSelect(nextChapter)}
                className="rounded-full px-3"
                title="Next chapter"
              >
                <ChevronRight className="w-4 h-4" />
              </Button>
            )}
            <Button
              size="sm"
              variant="primary"
              onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
              className="rounded-full px-3"
              title="Lên đầu trang"
            >
              <ArrowUp className="w-4 h-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
};

export default StoryPage;





