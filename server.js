// server.js
const express = require('express');
const { GoogleGenerativeAI } = require('@google/generative-ai');
const sharp = require('sharp');

// --- CONFIGURATION ---
const app = express();
const port = 3000;
// ❗️ IMPORTANT: Replace this with your actual Gemini API Key
const API_KEY = "AIzaSyDj9n1nreGm_JbhLCFpdSWzjVzcXTkhf8s";

// --- INITIALIZE GEMINI CLIENT ---
const genAI = new GoogleGenerativeAI(API_KEY);

// --- INITIALIZE MODELS ---
const textModel = genAI.getGenerativeModel({ model: "gemini-flash-lite-latest" });
const imageModel = genAI.getGenerativeModel({ model: "gemini-2.5-flash-image" });


// --- MIDDLEWARE ---
app.use(express.json());
app.use(express.static(__dirname));

// --- API ENDPOINT ---
app.post('/generate', async (req, res) => {
    const { type, characterData } = req.body;

    if (!characterData) {
        return res.status(400).json({ error: 'Character data is missing.' });
    }

    try {
        if (type === 'story') {
            const prompt = createStoryPrompt(characterData);
            const result = await textModel.generateContent(prompt);
            const text = result.response.text();
            res.json({ text: text });

        } else if (type === 'portrait') {
            // Server-side validation for required fields
            const requiredFields = ['race', 'class', 'equipment', 'appearance', 'setting'];
            for (const field of requiredFields) {
                if (!characterData[field] || characterData[field].trim() === '') {
                    console.error(`Validation failed: Field '${field}' is incomplete.`);
                    return res.status(400).json({
                        error: `Incomplete character data. Please provide a value for '${field}'.`
                    });
                }
            }

            const prompt = createImagePrompt(characterData);
            console.log("Sending detailed prompt to Gemini Image Model...");

            const result = await imageModel.generateContent(prompt);
            const response = await result.response;
            
            // The response can have multiple parts (e.g., text and an image).
            // We need to get the whole array of parts.
            const parts = response.candidates?.[0]?.content?.parts;

            // Now, find the specific part that contains the image data.
            const imagePart = parts?.find(part => part.inlineData && part.inlineData.data);
            
            if (imagePart) {
                const pngBase64 = imagePart.inlineData.data;
                console.log("Image part found! Converting to JPG...");

                const pngBuffer = Buffer.from(pngBase64, 'base64');
                const jpgBase64 = await sharp(pngBuffer)
                    .jpeg({ quality: 90 })
                    .toBuffer()
                    .then(buffer => buffer.toString('base64'));

                console.log("Conversion successful. Sending JPG to client.");
                res.json({ image_base_64: jpgBase64, mime_type: 'image/jpeg' });

            } else {
                // If we still can't find an image, log all text parts for better debugging.
                const textFeedback = parts?.map(part => part.text).join('\n') || "No text or image feedback from API.";
                console.error("API did not return an image part. Text feedback:", textFeedback);
                throw new Error(`API did not generate an image. It responded with: "${textFeedback}"`);
            }
        } else {
            res.status(400).json({ error: 'Invalid generation type.' });
        }
    } catch (error) {
        console.error('AI Generation Error:', error);
        res.status(500).json({ error: error.message });
    }
});


// --- HELPER FUNCTIONS ---
function createStoryPrompt(data) {
    return `Write a short, compelling origin story (around 200-300 words) for a Dungeons & Dragons character.

    Here are the character's details:
    - Name: ${data.name}
    - Gender: ${data.gender}
    - Race: ${data.race} (${data.subrace || 'Standard'})
    - Class: ${data.class}
    - Background: ${data.background}
    - Alignment: ${data.alignment}
    - Stats: STR(${data.stats.str}), DEX(${data.stats.dex}), CON(${data.stats.con}), INT(${data.stats.int}), WIS(${data.stats.wis}), CHA(${data.stats.cha})

    The story should hint at why they became a ${data.class} and how their ${data.background} background shaped them. Make it engaging and give them a clear motivation for adventuring.`;
}

function createImagePrompt(data) {
     return `Full body portrait of a Dungeons & Dragons character.
     
     The character is a level-${data.level} ${data.gender} ${data.race} ${data.class}.
     They have a ${data.background.toLowerCase()} background and a ${data.alignment.toLowerCase()} alignment.
     
     Appearance and Pose: ${data.appearance}.
     
     Equipment: They are wearing and equipped with ${data.equipment}.
     
     Setting: The scene is set ${data.setting}.
     
     Style: Photorealistic, cinematic digital painting, epic and adventurous mood, dramatic lighting, high detail, fantasy art, trending on ArtStation.
     
     Important: Do not include any text, letters, or words in the image.`;
}


// --- START THE SERVER ---
app.listen(port, () => {
    console.log(`Server running! Open http://localhost:${port} in your browser.`);
});
