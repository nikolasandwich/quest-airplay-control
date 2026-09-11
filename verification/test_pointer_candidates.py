import unittest
from PIL import Image, ImageDraw
from analyze_pointer_frames import candidates


class CandidatesTest(unittest.TestCase):
    def canvas(self):
        return Image.new('RGB',(100,100),'white')

    def test_round_candidate_is_not_verified_identity(self):
        image=self.canvas(); ImageDraw.Draw(image).ellipse((30,30,50,50),fill='black')
        result=candidates(image)
        self.assertEqual(len(result),1)
        self.assertEqual((result[0]['x'],result[0]['y']),(40,40))
        self.assertEqual(result[0]['identity'],'unverified')

    def test_page_circle_and_cursor_are_indistinguishable_without_feedback(self):
        image=self.canvas(); draw=ImageDraw.Draw(image)
        draw.ellipse((10,10,30,30),fill='black'); draw.ellipse((60,60,80,80),fill='black')
        self.assertEqual(len(candidates(image)),2)

    def test_square_and_hollow_ring_are_rejected(self):
        image=self.canvas(); draw=ImageDraw.Draw(image)
        draw.rectangle((10,10,30,30),fill='black'); draw.ellipse((60,60,80,80),outline='black',width=2)
        self.assertEqual(candidates(image),[])

    def test_hidden_or_low_contrast_pointer_has_no_position(self):
        image=self.canvas(); ImageDraw.Draw(image).ellipse((30,30,50,50),fill=(250,250,250))
        self.assertEqual(candidates(image),[])

    def test_edge_clipped_circle_rejected(self):
        image=self.canvas(); ImageDraw.Draw(image).ellipse((-4,30,16,50),fill='black')
        self.assertEqual(candidates(image),[])


if __name__ == '__main__':
    unittest.main()
